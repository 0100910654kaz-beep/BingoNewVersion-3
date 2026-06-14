package servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet(\"/BingoServlet\")
public class BingoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding(\"UTF-8\");
        String action = request.getParameter(\"action\");
        ServletContext application = getServletContext();
        HttpSession session = request.getSession();
        
        BingoGame game = (BingoGame) application.getAttribute(\"game\");

        // ⏱️ 1. 定期自動期限チェック
        // すでに部屋が存在している場合のみ、期限切れチェックを行います。
        if (game != null) {
            if (game.isExpired() || game.isPast2HoursFromLastBingo()) {
                application.removeAttribute(\"game");
                game = null;
            }
        }

        // 🚀 2. 【改善ポイント】「新しい部屋を作る」ボタンが押された時だけ部屋を作成する
        if (\"create\".equals(action)) {
            String validDaysStr = request.getParameter(\"validDays\");
            int validDays = 8; 
            if (validDaysStr != null) {
                try {
                    validDays = Integer.parseInt(validDaysStr);
                } catch (NumberFormatException e) {
                    validDays = 8;
                }
            }
            
            // 参加者が入力しやすい「簡単な4桁の数字」を全自動で抽選
            List<Integer> pool = new ArrayList<>();
            for (int i = 1000; i <= 9999; i++) { pool.add(i); }
            Collections.shuffle(pool);
            String randomId = String.valueOf(pool.get(0));
            
            // ここで初めて新しくゲームを作成してサーバーに保存
            game = new BingoGame(randomId, validDays);
            application.setAttribute(\"game", game);
            
            // 司会者をログイン状態にして管理画面へ飛ばす
            session.setAttribute(\"role\", \"admin\");
            response.sendRedirect(\"BingoServlet?action=admin\");
            return;
        }

        // 🛑 3. 【改善ポイント】ボタンを押す前の初期状態（まだ部屋がない）なら、勝手に作らずログイン前画面を出す
        if (game == null) {
            request.setAttribute(\"game\", null);
            request.getRequestDispatcher(\"index.jsp\").forward(request, response);
            return;
        }

        // --- 💡 これ以降のゲーム進行ロジック、レイアウト連携は元のコードのまま一切変えていません 💡 ---

        // 3. 司会者：管理画面の表示要求
        if (\"admin\".equals(action)) {
            String role = (String) session.getAttribute(\"role\");
            if (!\"admin\".equals(role)) {
                request.setAttribute(\"error\", \"⚠️ 司会者としての権限がありません。\");
                request.setAttribute(\"game\", game);
                request.getRequestDispatcher(\"index.jsp\").forward(request, response);
                return;
            }
            request.setAttribute(\"game\", game);
            request.getRequestDispatcher(\"admin.jsp\").forward(request, response);
            return;
        }

        // 4. 司会者：数字の抽選（ドロー）
        if (\"draw\".equals(action)) {
            String role = (String) session.getAttribute(\"role\");
            if (\"admin\".equals(role)) {
                List<Integer> drawn = game.getDrawnNumbers();
                if (drawn.size() < 75) {
                    List<Integer> allNumbers = new ArrayList<>();
                    for (int i = 1; i <= 75; i++) {
                        if (!drawn.contains(i)) { allNumbers.add(i); }
                    }
                    Collections.shuffle(allNumbers);
                    int luckyNumber = allNumbers.get(0);
                    drawn.add(luckyNumber);
                }
            }
            response.sendRedirect(\"BingoServlet?action=admin\");
            return;
        }

        // 5. 司会者：ゲームのリセット（初期化）
        if (\"reset\".equals(action)) {
            String role = (String) session.getAttribute(\"role\");
            if (\"admin\".equals(role)) {
                application.removeAttribute(\"game\");
                session.removeAttribute(\"role\");
            }
            response.sendRedirect(\"BingoServlet\");
            return;
        }

        // 6. プレイヤー：名前と部屋番号を入力して参加（カード生成）
        if (\"join\".equals(action)) {
            String inputGameId = request.getParameter(\"gameId\");
            String inputName = request.getParameter(\"playerName\");
            
            if (inputName == null || inputName.trim().isEmpty()) {
                inputName = \"匿名希望\";
            } else {
                inputName = inputName.trim();
            }
            
            if (game.getGameId().equals(inputGameId)) {
                String confirmedName = inputName;
                
                if (!game.getAllPlayers().contains(confirmedName)) {
                    game.getAllPlayers().add(confirmedName);
                    
                    List<List<String>> card = new ArrayList<>();
                    List<List<Integer>> columns = new ArrayList<>();
                    int[][] ranges = { {1,15}, {16,30}, {31,45}, {46,60}, {61,75} };
                    
                    for (int i = 0; i < 5; i++) {
                        List<Integer> pool = new ArrayList<>();
                        for (int n = ranges[i][0]; n <= ranges[i][1]; n++) { pool.add(n); }
                        Collections.shuffle(pool);
                        columns.add(pool.subList(0, 5));
                    }
                    
                    for (int r = 0; r < 5; r++) {
                        List<String> row = new ArrayList<>();
                        for (int c = 0; c < 5; c++) {
                            if (r == 2 && c == 2) { row.add(\"0\"); } 
                            else { row.add(String.valueOf(columns.get(c).get(r))); }
                        }
                        card.add(row);
                    }
                    
                    session.setAttribute(\"card\", card);
                    session.setAttribute(\"myConfirmedName\", confirmedName);
                } else {
                    session.setAttribute(\"myConfirmedName\", confirmedName);
                }
                
                List<List<String>> currentCard = (List<List<String>>) session.getAttribute(\"card\");
                game.setPlayerCard(confirmedName, currentCard);
                
                request.setAttribute(\"game\", game);
                request.setAttribute(\"confirmedPlayerName\", confirmedName);
                request.getRequestDispatcher(\"index.jsp\").forward(request, response);
            } else {
                request.setAttribute(\"error\", \"⚠️ 部屋番号（ゲームID）が正しくありません。\");
                request.getRequestDispatcher(\"index.jsp\").forward(request, response);
            }
            return;
        }

        String userType = request.getParameter(\"userType\");
        request.setAttribute(\"game\", game);
        
        if (\"admin\".equals(userType)) {
            request.getRequestDispatcher(\"admin.jsp\").forward(request, response);
        } else {
            String confirmedName = (String) session.getAttribute(\"myConfirmedName\");
            if (confirmedName == null) {
                confirmedName = request.getParameter(\"playerName\");
            }
            request.setAttribute(\"confirmedPlayerName\", confirmedName);
            request.getRequestDispatcher(\"index.jsp\").forward(request, response);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
