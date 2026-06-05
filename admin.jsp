<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="servlet.BingoGame" %>
<%@ page import="servlet.PlayerResult" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%
    BingoGame game = (BingoGame) request.getAttribute("game");
    String gameId = (game != null) ? game.getGameId() : "まだ開始していません";

    List<Integer> reverseDrawnNumbers = new ArrayList<>();
    int ballCount = 0;
    if (game != null) {
        reverseDrawnNumbers.addAll(game.getDrawnNumbers());
        ballCount = reverseDrawnNumbers.size();
        Collections.reverse(reverseDrawnNumbers);
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>ビンゴ大会 - 司会者画面</title>
    <style>
        body { font-family: Arial, sans-serif; background-color: #eef2f3; padding: 20px; text-align: center; }
        .admin-container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); }
        h1 { color: #2b3a42; margin-bottom: 20px; }
        .info-panel { background: #f0f4f8; padding: 15px; border-radius: 8px; margin-bottom: 25px; font-size: 18px; }
        .big-number { font-size: 80px; font-weight: bold; color: #ff6b6b; background: #ffe3e3; display: inline-block; padding: 10px 50px; border-radius: 15px; margin: 15px 0; border: 3px solid #ff6b6b; }
        .control-box { margin: 20px 0; }
        .btn { display: inline-block; padding: 14px 28px; font-size: 20px; font-weight: bold; color: white; border: none; border-radius: 6px; cursor: pointer; margin: 10px; text-decoration: none; }
        .btn-draw { background-color: #2b8a3e; box-shadow: 0 4px #1e622b; border: none; }
        .btn-draw:active { transform: translateY(4px); box-shadow: none; }
        .btn-reset { background-color: #e63946; font-size: 16px; padding: 10px 20px; border: none; }
        .btn-screen { background-color: #4a90e2; font-size: 16px; padding: 10px 20px; border: none; }
        
        .flex-box { display: flex; justify-content: space-between; margin-top: 30px; gap: 20px; }
        .panel { flex: 1; background: #f9f9f9; padding: 15px; border-radius: 8px; text-align: left; box-shadow: inset 0 0 5px rgba(0,0,0,0.05); }
        .panel h3 { margin-top: 0; color: #2b3a42; border-bottom: 2px solid #ddd; padding-bottom: 5px; }
        
        .history-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 8px; margin-top: 10px; }
        .history-cell { background: #ddd; padding: 8px; font-size: 16px; font-weight: bold; border-radius: 4px; text-align: center; color: #444; }
        .history-cell.newest { background: #ff6b6b; color: white; animation: blink 0.8s infinite alternate; }
        @keyframes blink { from { opacity: 1; } to { opacity: 0.7; } }
        ul { padding-left: 20px; }
        li { margin-bottom: 8px; font-size: 16px; }
    </style>

    <script>
        let screenWindow = null;

        window.addEventListener("keydown", function(event) {
            if (event.key === "Enter") {
                let drawBtn = document.getElementById("drawButton");
                if (drawBtn) { event.preventDefault(); drawBtn.click(); }
            }
            if (event.key === "Escape") { event.preventDefault(); confirmReset(); }
        });

        function confirmReset() {
            if (confirm("⚠️ 本当にビンゴゲームをリセットしますか？")) {
                window.location.href = "BingoServlet?action=reset";
            }
        }

        function openProjectorScreen() {
            screenWindow = window.open("", "BingoProjector", "width=1000,height=750,top=100,left=100,resizable=yes");
            
            let htmlContent = '<html><head><title>ビンゴ中継大画面</title>' +
            '<style>' +
            'body { font-family: Arial, sans-serif; background-color: #1a1a1a; color: white; text-align: center; padding: 40px; margin: 0; }' +
            '.title { font-size: 42px; color: #ff6b6b; font-weight: bold; margin-bottom: 20px; letter-spacing: 4px; }' +
            '.num-display { font-size: 180px; font-weight: bold; color: #fff; background: #ff6b6b; padding: 20px 100px; border-radius: 30px; display:inline-block; margin:20px 0; line-height:1; min-width:200px; }' +
            '.grid { display: grid; grid-template-columns: repeat(10, 1fr); gap: 15px; max-width: 1100px; margin: 0 auto; padding: 20px; background: #2a2a2a; border-radius: 15px; min-height:80px; }' +
            '.cell { background: #444; padding: 15px 0; font-size: 32px; font-weight: bold; border-radius: 8px; color: #bbb; text-align:center; }' +
            '.cell.new { background: #ff6b6b; color: white; font-size: 42px; box-shadow: 0 0 25px #ff6b6b; animation: scaleUp 0.4s ease-out; }' +
            '.winner-box { font-size: 24px; background: #333; padding: 15px; border-radius: 10px; max-width: 600px; margin: 30px auto 0; text-align: left; border-left: 8px solid #ff6b6b; }' +
            '@keyframes scaleUp { from { transform:scale(0.5); } to { transform:scale(1); } }' +
            '</style></head><body>' +
            '<div class="title">🎉 ビンゴ大会 抽選生中継 🎉</div>' +
            '<div><div style="font-size:28px; color:#aaa;">現在の当選番号</div><div class="num-display" id="p-num">待機中</div></div>' +
            '<div id="p-ball" style="font-size:24px; margin:10px 0; color:#ffb74d; font-weight:bold;"></div>' +
            '<div style="font-size:28px; text-align:left; max-width:1100px; margin:20px auto 10px; color:#aaa;">📊 出た数字の履歴（最新が左上）</div>' +
            '<div class="grid" id="p-grid"></div>' +
            '<div class="winner-box"><strong>🏆 ビンゴ達成者上位:</strong><br><ul id="p-list"></ul></div>' +
            '</body></html>';
            
            if (screenWindow.document.getElementById('p-num') === null) {
                screenWindow.document.open();
                screenWindow.document.write(htmlContent);
                screenWindow.document.close();
            }
            updateProjectorData(); 
        }

        function updateProjectorData() {
            if (screenWindow && !screenWindow.closed) {
                try {
                    let pNum = screenWindow.document.getElementById('p-num');
                    let pGrid = screenWindow.document.getElementById('p-grid');
                    let pList = screenWindow.document.getElementById('p-list');
                    let pBall = screenWindow.document.getElementById('p-ball');

                    if(pNum) pNum.innerText = document.querySelector('.big-number').innerText;
                    if(pBall) pBall.innerText = document.getElementById('adminBallCounter').innerText;
                    if(pGrid) pGrid.innerHTML = document.querySelector('.history-grid').innerHTML.replace(/history-cell/g, 'cell').replace(/newest/g, 'new');
                    if(pList) pList.innerHTML = document.getElementById('bingoList').innerHTML;
                } catch(e) {
                    console.log("大画面へのデータ書き換え待機中...");
                }
            }
        }

        setInterval(function() {
            fetch('BingoServlet?userType=admin')
                .then(response => response.text())
                .then(html => {
                    let parser = new DOMParser();
                    let doc = parser.parseFromString(html, 'text/html');
                    if(doc.querySelector('.admin-container')) {
                        document.querySelector('.admin-container').innerHTML = doc.querySelector('.admin-container').innerHTML;
                        updateProjectorData();
                    }
                });
        }, 5000);

        window.onload = function() {
            screenWindow = window.open("", "BingoProjector");
            if (screenWindow && screenWindow.document.getElementById('p-num') !== null) {
                updateProjectorData();
            }
        };
    </script>
</head>
<body>

<div class="admin-container">
    <div style="display: flex; justify-content: space-between; align-items: center;">
        <button type="button" class="btn btn-reset" onclick="confirmReset()">🔄 リセット [Esc]</button>
        <h1>🎤 司会者コントロール画面 🎤</h1>
        <button type="button" class="btn btn-screen" onclick="openProjectorScreen()">📺 大画面を開く</button>
    </div>

    <% if (game == null) { %>
        <div class="info-panel" style="background: #ffe3e3;">
            <p>まだビンゴゲームの部屋が作成されていません。</p>
            <form action="BingoServlet" method="get">
                <input type="hidden" name="action" value="create">
                <label>部屋の有効日数: </label>
                <input type="number" name="validDays" value="8" style="width:50px; padding:5px; text-align:center;"> 日間<br><br>
                <button type="submit" class="btn btn-draw" style="box-shadow:none;">🚀 新規ビンゴ部屋(88888888)を開始する</button>
            </form>
        </div>
    <% } else { %>
        <div class="info-panel">
            <strong>現在の管理部屋ID:</strong> <span style="color:#4a90e2; font-weight:bold;"><%= gameId %></span> &nbsp;&nbsp;|&nbsp;&nbsp;
            <strong>現在の総参加人数:</strong> <span style="color:#2b8a3e; font-weight:bold;"><%= game.getPlayerCount() %> 名</span>
        </div>

        <p style="font-size: 18px; margin-bottom: 0;">抽選された最新の数字</p>
        <div class="big-number"><%= game.getDrawnNumbers().isEmpty() ? "待機中" : game.getDrawnNumbers().get(game.getDrawnNumbers().size() - 1) %></div>

        <div id="adminBallCounter" style="font-size:18px; font-weight:bold; color:#555; margin-bottom:10px;">
            残り玉数: <%= 75 - ballCount %> 球
        </div>

        <div class="control-box">
            <a href="BingoServlet?action=draw" id="drawButton" class="btn btn-draw" onclick="setTimeout(updateProjectorData, 50);">🎲 次の数字を引く [Enter]</a>
        </div>

        <div class="flex-box">
            <div class="panel" style="flex: 1.4;">
                <h3>📊 出た数字の履歴（最新が左上）</h3>
                <div class="history-grid">
                    <% for (int i = 0; i < reverseDrawnNumbers.size(); i++) { 
                        int num = reverseDrawnNumbers.get(i);
                        if (i == 0) { %>
                            <div class="history-cell newest"><%= num %></div>
                        <% } else { %>
                            <div class="history-cell"><%= num %></div>
                        <% }
                    } %>
                </div>
            </div>

            <div class="panel">
                <h3>🏆 ビンゴ達成者一覧</h3>
                <ul id="bingoList">
                    <% 
                       List<PlayerResult> bingoList = game.getBingoPlayers();
                       int totalCount = bingoList.size();
                       
                       for (int i = 0; i < totalCount; i++) {
                           PlayerResult p = bingoList.get(i);
                           int currentRank = totalCount - i; 
                    %>
                        <li><strong><%= currentRank %>位</strong>: <%= p.getPlayerName() %> さん <span style="color:#e63946; font-weight:bold;">(🔑<%= p.getDrawnNumberAtBingo() %>番でビンゴ!)</span></li>
                    <% 
                       } 
                       if (bingoList.isEmpty()) { %> <p style="color:#888;">まだビンゴした人はいません</p> <% } 
                    %>
                </ul>

                <h3 style="margin-top: 25px;">🔥 リーチの人（全自動検知）</h3>
                <ul>
                    <% for (PlayerResult p : game.getReachPlayers()) { %>
                        <li><strong><%= p.getPlayerName() %> さん</strong> <span style="color: #ff9800; font-size: 14px; font-weight: bold;">（あと <%= game.getWaitNumbers(p.getPlayerName()) %> 番でビンゴ！）</span></li>
                    <% } 
                       if (game.getReachPlayers().isEmpty()) { %> <p style="color:#888;">まだリーチの人はいません</p> <% } %>
                </ul>
            </div>
        </div>
    <% } %>
</div>
</body>
</html>
