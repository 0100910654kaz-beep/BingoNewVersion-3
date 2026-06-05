<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="servlet.BingoGame" %>
<%@ page import="servlet.PlayerResult" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%
    BingoGame game = (BingoGame) request.getAttribute("game");
    String error = (String) request.getAttribute("error");
    String gameId = (game != null) ? game.getGameId() : "";
    
    String playerName = (String) request.getAttribute("confirmedPlayerName");
    if (playerName == null) {
        playerName = (String) session.getAttribute("myConfirmedName");
    }
    if (playerName == null) { playerName = ""; }

    List<List<String>> bingoCard = (List<List<String>>) session.getAttribute("card");

    List<Integer> reverseDrawnNumbers = new ArrayList<>();
    int ballCount = 0;
    if (game != null) {
        reverseDrawnNumbers.addAll(game.getDrawnNumbers());
        ballCount = reverseDrawnNumbers.size();
        Collections.reverse(reverseDrawnNumbers);
    }

    boolean myBingo = false;
    if (game != null && !playerName.isEmpty()) {
        for (PlayerResult p : game.getBingoPlayers()) {
            if (p.getPlayerName().equals(playerName)) {
                myBingo = true;
                break;
            }
        }
    }
    
    boolean myReach = false;
    if (game != null && !playerName.isEmpty() && !myBingo) {
        for (PlayerResult p : game.getReachPlayers()) {
            if (p.getPlayerName().equals(playerName)) {
                myReach = true;
                break;
            }
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ビンゴ大会 - プレイヤー画面</title>
    <script src="https://cdn.jsdelivr.net/npm/canvas-confetti@1.6.0/dist/confetti.browser.min.js"></script>
    <style>
        body { font-family: Arial, sans-serif; text-align: center; background-color: #f0f0f0; padding: 20px; transition: background 0.5s; }
        .container { max-width: 500px; margin: 0 auto; background: white; padding: 20px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        h1 { color: #ff6b6b; margin-top: 5px; }
        .error { color: red; font-weight: bold; margin-bottom: 20px; }
        .info-box { background: #eef2f3; padding: 10px; border-radius: 5px; margin-bottom: 20px; }
        .number-box { font-size: 48px; font-weight: bold; color: #2b2b2b; background: #ffe3e3; display: inline-block; padding: 10px 30px; border-radius: 10px; margin: 10px 0; }
        .input-text { padding: 10px; font-size: 16px; width: 80%; max-width: 300px; margin-bottom: 10px; border: 1px solid #ccc; border-radius: 5px; text-align: center; }
        .list-box { text-align: left; background: #f9f9f9; padding: 10px; border-radius: 5px; margin-top: 20px; }
        
        /* 状態別ワクワク演出スタイル */
        .status-panel { font-size: 24px; font-weight: bold; padding: 15px; border-radius: 8px; margin-bottom: 15px; border: 2px solid transparent; }
        .status-normal { background: #e3f2fd; color: #2196f3; }
        .status-reach { background: #fff3e0; color: #e65100; border-color: #ffb74d; animation: pulse 0.5s infinite alternate; }
        .status-bingo { background: #fffde7; color: #e6c200; border-color: #ffd700; animation: goldPulse 0.5s infinite alternate; font-size: 28px; }

        .bingo-table { margin: 20px auto; border-collapse: collapse; background: #fff; box-shadow: 0 4px 8px rgba(0,0,0,0.1); border-radius: 8px; overflow: hidden; }
        .bingo-cell { width: 60px; height: 60px; border: 2px solid #ddd; font-size: 22px; font-weight: bold; text-align: center; vertical-align: middle; color: #333; }
        .hit { background-color: #ff6b6b !important; color: white !important; }
        .free-cell { background-color: #ffe3e3; color: #ff6b6b; font-size: 14px; }

        .history-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin-top: 10px; }
        .history-cell { background: #e0e0e0; padding: 8px; font-size: 16px; font-weight: bold; border-radius: 4px; text-align: center; color: #555; }
        .history-cell.newest { background: #ff6b6b; color: white; }
        
        @keyframes pulse { from { transform: scale(1); } to { transform: scale(1.02); } }
        @keyframes goldPulse { from { transform: scale(1); box-shadow: 0 0 10px #ffd700; } to { transform: scale(1.04); box-shadow: 0 0 20px #ffd700; } }
    </style>

    <% if (game != null) { %>
    <script>
        let alreadyBingo = <%= myBingo %>;
        let isFirstLoad = true; 

        function launchFireworks() {
            let duration = 5 * 1000;
            let end = Date.now() + duration;
            (function frame() {
                confetti({ particleCount: 6, angle: 60, spread: 60, origin: { x: 0, y: 0.8 } });
                confetti({ particleCount: 6, angle: 120, spread: 60, origin: { x: 1, y: 0.8 } });
                if (Date.now() < end) { requestAnimationFrame(frame); }
            }());
        }

        window.onload = function() {
            // 初回起動時、すでに当選履歴（玉数）が1個以上ある場合のみ、過去のビンゴとして花火を上げる（初期FREEでの誤爆防止）
            if (alreadyBingo && <%= ballCount %> > 0) { launchFireworks(); }
            isFirstLoad = false;
        };

        function checkUpdate() {
            fetch('BingoServlet?userType=player&playerName=<%= java.net.URLEncoder.encode(playerName, "UTF-8") %>')
                .then(response => response.text())
                .then(html => {
                    if(!html) return;
                    let parser = new DOMParser();
                    let doc = parser.parseFromString(html, 'text/html');
                    
                    if (doc.querySelector('.error')) { window.location.reload(); return; }
                    
                    document.querySelector('.number-box').innerHTML = doc.querySelector('.number-box').innerHTML;
                    document.querySelector('.bingo-table').innerHTML = doc.querySelector('.bingo-table').innerHTML;
                    document.querySelector('.history-grid').innerHTML = doc.querySelector('.history-grid').innerHTML;
                    document.getElementById('statusArea').innerHTML = doc.getElementById('statusArea').innerHTML;
                    document.getElementById('ballCounter').innerHTML = doc.getElementById('ballCounter').innerHTML;

                    let statePanel = doc.querySelector('.status-panel');
                    if (statePanel && statePanel.classList.contains('status-bingo') && !alreadyBingo && !isFirstLoad) {
                        alreadyBingo = true;
                        launchFireworks();
                    }
                })
                .catch(err => console.log(err));
        }
        setInterval(checkUpdate, 5000);
    </script>
    <% } %>
</head>
<body>
<div class="container">
    <h1>🎉 ビンゴ大会 🎉</h1>

    <% if (error != null) { %>
        <div class="error"><%= error %></div>
    <% } %>

    <% if (game == null) { %>
        <div class="info-box">
            <p>参加する部屋の「部屋番号（ゲームID）」を入力してください。</p>
            <form action="BingoServlet" method="get">
                <input type="hidden" name="action" value="join">
                <input type="text" name="gameId" class="input-text" placeholder="8桁の部屋番号を入力" required><br>
                <input type="text" name="playerName" class="input-text" placeholder="あなたの名前（空欄でも参加可能）"><br>
                <button type="submit" style="padding: 12px 24px; font-size: 18px; font-weight: bold; color: white; background-color: #4caf50; border: none; border-radius: 5px; cursor: pointer; margin: 10px;">ゲームに参加する</button>
            </form>
        </div>
    <% } else { %>
        <div class="info-box">
            <strong>部屋番号 (ID):</strong> <%= gameId %>&nbsp;&nbsp;|&nbsp;&nbsp;
            <strong>名前:</strong> <span style="color:#2b8a3e; font-weight:bold;"><%= playerName %> さん</span>
        </div>

        <div id="statusArea">
            <% if (myBingo) { %>
                <div class="status-panel status-bingo">👑 ビンゴ達成！！！おめでとうございます！ 🎉</div>
            <% } else if (myReach) { %>
                <div class="status-panel status-reach">🔥 リーチ発生中！ドキドキ…そのままお待ちください！</div>
            <% } else { %>
                <div class="status-panel status-normal">🎯 ナンバー抽選中...（全自動判定モード）</div>
            <% } %>
        </div>

        <p style="margin-bottom:0;">現在の最新の当選番号</p>
        <div class="number-box">
            <%= game.getDrawnNumbers().isEmpty() ? "待機中" : game.getDrawnNumbers().get(game.getDrawnNumbers().size() - 1) %>
        </div>
        
        <div id="ballCounter" style="font-size:16px; font-weight:bold; color:#555; margin-bottom:10px;">
            現在の玉数: <%= ballCount %> / 75 球
        </div>

        <% if (bingoCard != null) { %>
            <table class="bingo-table">
                <% for (int r = 0; r < 5; r++) { %>
                    <tr>
                        <% for (int c = 0; c < 5; c++) { 
                            String num = bingoCard.get(r).get(c);
                            boolean isHit = game.getDrawnNumbers().contains(Integer.parseInt(num)) || num.equals("0");
                            if (num.equals("0")) { %>
                                <td class="bingo-cell free-cell hit">FREE</td>
                            <% } else { %>
                                <td class="bingo-cell <%= isHit ? "hit" : "" %>"><%= num %></td>
                            <% } 
                        } %>
                    </tr>
                <% } %>
            </table>
        <% } %>

        <div class="list-box">
            <h3 style="margin: 5px 0;">📊 出た数字一覧（最新が左上）</h3>
            <div class="history-grid">
                <% for (int i = 0; i < reverseDrawnNumbers.size(); i++) { 
                    int num = reverseDrawnNumbers.get(i);
                    if (i == 0) { %>
                        <div class="history-cell newest" style="background:#ff6b6b; color:white;"><%= num %></div>
                    <% } else { %>
                        <div class="history-cell"><%= num %></div>
                    <% }
                } %>
            </div>
        </div>
    <% } %>
</div>
</body>
</html>
