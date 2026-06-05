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
            
            String newGameId = "88888888"; 
            game = new BingoGame(newGameId, validDays);
            application.setAttribute("game", game);
            
            List<Integer> shuffledNumbers = new CopyOnWriteArrayList<>();
            for (int i = 1; i <= 75; i++) {
                shuffledNumbers.add(i);
            }
            Collections.shuffle(shuffledNumbers);
            application.setAttribute("shuffledNumbers", shuffledNumbers);
            
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        else if ("reset".equals(action)) {
            application.removeAttribute("game");
            application.removeAttribute("shuffledNumbers");
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        if (game == null) {
            request.setAttribute("error", "⚠️ 現在ビンゴゲームは開始されていないか、リセットされました。");
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        if (game.isExpired() || game.isPast2HoursFromLastBingo()) {
            application.removeAttribute("game"); 
            application.removeAttribute("shuffledNumbers");
            request.setAttribute("error", "🔒 この部屋は安全のため自動ロック（削除）されました。新しく作り直してください。");
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        if ("draw".equals(action)) {
            @SuppressWarnings("unchecked")
            List<Integer> shuffledNumbers = (List<Integer>) application.getAttribute("shuffledNumbers");
            
            if (shuffledNumbers != null && !shuffledNumbers.isEmpty()) {
                int nextNumber = shuffledNumbers.remove(0);
                game.getDrawnNumbers().add(nextNumber);
                application.setAttribute("shuffledNumbers", shuffledNumbers);
                
                // 全プレイヤーのカードを一斉自動スキャン
                game.checkAllPlayers();
            }
            
            request.setAttribute("game", game);
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        else if ("join".equals(action)) {
            String inputId = request.getParameter("gameId");
            String inputName = request.getParameter("playerName");
            
            if (game.getGameId().equals(inputId)) {
                String confirmedName = game.registerPlayer(inputName);
                
                @SuppressWarnings("unchecked")
                List<List<String>> card = (List<List<String>>) session.getAttribute("card");
                if (card == null) {
                    card = new ArrayList<>();
                    List<List<Integer>> columns = new ArrayList<>();
                    for (int i = 0; i < 5; i++) {
                        List<Integer> pool = new ArrayList<>();
                        for (int j = 1; j <= 15; j++) {
                            pool.add((i * 15) + j);
                        }
                        Collections.shuffle(pool);
                        columns.add(pool.subList(0, 5));
                    }
                    
                    for (int r = 0; r < 5; r++) {
                        List<String> row = new ArrayList<>();
                        for (int c = 0; c < 5; c++) {
                            if (r == 2 && c == 2) {
                                row.add("0"); 
                            } else {
                                row.add(String.valueOf(columns.get(c).get(r)));
                            }
                        }
                        card.add(row);
                    }
                    session.setAttribute("card", card);
                    session.setAttribute("myConfirmedName", confirmedName);
                }
                
                // カードをサーバー頭脳に登録して初期スキャン
                game.setPlayerCard(confirmedName, card);
                
                request.setAttribute("game", game);
                request.setAttribute("confirmedPlayerName", confirmedName);
                request.getRequestDispatcher("index.jsp").forward(request, response);
            } else {
                request.setAttribute("error", "⚠️ 部屋番号（ゲームID）が正しくありません。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
            }
            return;
        }

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
