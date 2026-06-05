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

@WebServlet("/BingoServlet")
public class BingoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");
        ServletContext application = getServletContext();
        HttpSession session = request.getSession();
        
        BingoGame game = (BingoGame) application.getAttribute("game");

        // 🚀 部屋の新規作成（バグの出ない完璧な4桁ランダム自動生成）
        if ("create".equals(action)) {
            String validDaysStr = request.getParameter("validDays");
            int validDays = 8; 
            if (validDaysStr != null) {
                try {
                    validDays = Integer.parseInt(validDaysStr);
                } catch (NumberFormatException e) {
                    validDays = 8;
                }
            }
            
            // 💡 1000 〜 9999 の4桁の数字を自動生成
            int random4Digit = (int)(Math.random() * 9000) + 1000;
            String newGameId = String.valueOf(random4Digit); 
            
            game = new BingoGame(newGameId, validDays);
            application.setAttribute("game", game);
            
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // リセット処理
        if ("reset".equals(action)) {
            application.removeAttribute("game");
            response.sendRedirect("admin.jsp");
            return;
        }

        // 自動タイマーロック（時間経過チェック）
        if (game != null) {
            if (game.isExpired() || game.isPast2HoursFromLastBingo()) {
                application.removeAttribute("game");
                game = null;
            }
        }

        // 司会者が数字を引く処理
        if ("draw".equals(action)) {
            if (game != null) {
                game.drawNumber();
            }
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // プレイヤーの参加・ログイン処理
        if ("join".equals(action)) {
            String inputId = request.getParameter("gameId");
            String playerName = request.getParameter("playerName");
            
            if (playerName == null || playerName.trim().isEmpty()) {
                playerName = "";
            } else {
                playerName = playerName.trim();
            }

            if (game != null && game.getGameId().equals(inputId)) {
                String confirmedName = (String) session.getAttribute("myConfirmedName");
                List<List<String>> bingoCard = (List<List<String>>) session.getAttribute("card");

                if (confirmedName == null || !confirmedName.equals(playerName) || bingoCard == null) {
                    confirmedName = game.registerPlayer(playerName);
                    
                    // 新しいビンゴカードの生成 (1〜75)
                    List<List<String>> card = new ArrayList<>();
                    List<List<Integer>> columns = new ArrayList<>();
                    
                    for (int i = 0; i < 5; i++) {
                        List<Integer> pool = new ArrayList<>();
                        for (int j = 1; j <= 15; j++) {
                            pool.add(i * 15 + j);
                        }
                        Collections.shuffle(pool);
                        columns.add(pool.subList(0, 5));
                    }

                    for (int r = 0; r < 5; r++) {
                        List<String> row = new ArrayList<>();
                        for (int c = 0; c < 5; c++) {
                            if (r == 2 && c == 2) {
                                row.add("0"); // 真ん中FREE
                            } else {
                                row.add(String.valueOf(columns.get(c).get(r)));
                            }
                        }
                        card.add(row);
                    }
                    
                    session.setAttribute("card", card);
                    session.setAttribute("myConfirmedName", confirmedName);
                }
                
                game.setPlayerCard(confirmedName, bingoCard != null ? bingoCard : (List<List<String>>) session.getAttribute("card"));
                
                request.setAttribute("game", game);
                request.setAttribute("confirmedPlayerName", confirmedName);
                request.getRequestDispatcher("index.jsp").forward(request, response);
            } else {
                request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
            }
            return;
        }

        // 通常アクセス時の画面振り分け
        String userType = request.getParameter("userType");
        request.setAttribute("game", game);
        
        if ("admin".equals(userType)) {
            request.getRequestDispatcher("admin.jsp").forward(request, response);
        } else {
            String confirmedName = (String) session.getAttribute("myConfirmedName");
            if (confirmedName == null) {
                confirmedName = request.getParameter("playerName");
            }
            request.setAttribute("confirmedPlayerName", confirmedName);
            request.getRequestDispatcher("index.jsp").forward(request, response);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        doGet(request, response);
    }
}
