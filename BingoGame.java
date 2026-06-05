package servlet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BingoGame implements Serializable {
    private static final long serialVersionUID = 1L;

    private String gameId;                             // 部屋番号（ゲームID）
    private List<Integer> drawnNumbers;                // 当選番号の履歴
    private List<PlayerResult> bingoPlayers;           // ビンゴ達成者のリスト
    private List<PlayerResult> reachPlayers;           // リーチ達成者のリスト
    private List<String> allPlayers;                   // 全参加者の名前リスト
    private Date expireTime;                           // この部屋の有効期限
    private Date lastBingoTime;                        // 最後にビンゴが出た時刻
    private int anonymousCount = 0;                    // 名前空欄の人用のカウンター

    // 各プレイヤーのカードデータをサーバー側でも管理・自動スキャンするための箱
    private ConcurrentHashMap<String, List<List<String>>> playerCards = new ConcurrentHashMap<>();
    // 各プレイヤーの「待ち数字（ビンゴする番号）」を記憶する箱
    private ConcurrentHashMap<String, List<String>> playerWaitNumbers = new ConcurrentHashMap<>();

    public BingoGame(String gameId, int validDays) {
        // 💡 渡された4桁のIDをそのまま確実にセット
        this.gameId = gameId;
        this.drawnNumbers = new CopyOnWriteArrayList<>();
        this.bingoPlayers = new CopyOnWriteArrayList<>();
        this.reachPlayers = new CopyOnWriteArrayList<>();
        this.allPlayers = new CopyOnWriteArrayList<>();
        this.lastBingoTime = null;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, validDays);
        this.expireTime = cal.getTime();
    }

    // 名前が空欄だった場合に自動的に「参加者1」「参加者2」と命名して登録する部品
    public synchronized String registerPlayer(String name) {
        if (name == null || name.trim().isEmpty()) {
            anonymousCount++;
            String assignedName = "参加者" + anonymousCount;
            allPlayers.add(assignedName);
            return assignedName;
        }
        String trimmed = name.trim();
        if (!allPlayers.contains(trimmed)) {
            allPlayers.add(trimmed);
        }
        return trimmed;
    }

    public int getPlayerCount() {
        return allPlayers.size();
    }

    // 🎲 1〜75から重複なくランダムに数字を引くメイン処理
    public void drawNumber() {
        if (drawnNumbers.size() >= 75) return;

        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= 75; i++) {
            if (!drawnNumbers.contains(i)) {
                pool.add(i);
            }
        }
        java.util.Collections.shuffle(pool);
        int chosen = pool.get(0);
        drawnNumbers.add(chosen);

        // 数字が引かれた瞬間に、全員のカードを全自動で裏スキャンして判定する
        checkAllPlayersStatus(chosen);
    }

    // プレイヤーが参加した時にカードを登録し、即座に状態を自動判定する
    public void setPlayerCard(String name, List<List<String>> card) {
        if (name == null || card == null) return;
        playerCards.put(name, card);
        
        int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
        checkSinglePlayerStatus(name, lastNum);
    }

    // 全員のカードを全自動で裏側で一括検知する処理
    private void checkAllPlayersStatus(int currentDrawnNumber) {
        for (String name : playerCards.keySet()) {
            checkSinglePlayerStatus(name, currentDrawnNumber);
        }
    }

    // 特定のプレイヤーのカードに「リーチ」や「ビンゴ」が起きているか全自動で調べる精密な計算
    private void checkSinglePlayerStatus(String name, int currentDrawnNumber) {
        List<List<String>> card = playerCards.get(name);
        if (card == null) return;

        boolean[][] hits = new boolean[5][5];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                String numStr = card.get(r).get(c);
                int num = Integer.parseInt(numStr);
                if (num == 0 || drawnNumbers.contains(num)) {
                    hits[r][c] = true;
                }
            }
        }

        List<List<String>> lines = new ArrayList<>();
        // 横5ライン
        for (int r = 0; r < 5; r++) {
            List<String> line = new ArrayList<>();
            for (int c = 0; c < 5; c++) line.add(r + "," + c);
            lines.add(line);
        }
        // 縦5ライン
        for (int c = 0; c < 5; c++) {
            List<String> line = new ArrayList<>();
            for (int r = 0; r < 5; r++) line.add(r + "," + c);
            lines.add(line);
        }
        // ななめ2ライン
        List<String> d1 = new ArrayList<>();
        List<String> d2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            d1.add(i + "," + i);
            d2.add(i + "," + (4 - i));
        }
        lines.add(d1);
        lines.add(d2);

        boolean holdsBingo = false;
        List<String> waitNumbers = new ArrayList<>();

        for (List<String> line : lines) {
            int missingCount = 0;
            String missingNumStr = "";

            for (String coord : line) {
                String[] parts = coord.split(",");
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[c]);
                if (!hits[r][c]) {
                    missingCount++;
                    missingNumStr = card.get(r).get(c);
                }
            }

            if (missingCount == 0) {
                holdsBingo = true;
            } else if (missingCount == 1) {
                if (!waitNumbers.contains(missingNumStr)) {
                    waitNumbers.add(missingNumStr);
                }
            }
        }

        if (holdsBingo) {
            addBingoPlayer(name, currentDrawnNumber);
        } else if (!waitNumbers.isEmpty()) {
            playerWaitNumbers.put(name, waitNumbers);
            addReachPlayer(name);
        } else {
            playerWaitNumbers.remove(name);
            removeReachPlayer(name);
        }
    }

    // ビンゴ達成者を記録する（最新が先頭に入る仕様）
    private void addBingoPlayer(String name, int currentDrawnNumber) {
        for (PlayerResult p : bingoPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        Date now = new Date();
        bingoPlayers.add(0, new PlayerResult(name, now, currentDrawnNumber));
        this.lastBingoTime = now;
        removeReachPlayer(name);
    }

    // リーチ登録（自動判定から呼ばれる）
    private void addReachPlayer(String name) {
        for (PlayerResult p : reachPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        reachPlayers.add(0, new PlayerResult(name, new Date(), 0));
    }

    // リーチ解除
    public void removeReachPlayer(String name) {
        reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
    }

    // 🚀 リーチの人の「待ち数字」を司会者画面に渡すための部品
    public List<String> getWaitNumbers(String name) {
        return playerWaitNumbers.getOrDefault(name, new ArrayList<>());
    }

    public boolean isExpired() { return new Date().after(this.expireTime); }
    public boolean isPast2HoursFromLastBingo() {
        if (bingoPlayers.isEmpty()) return false;
        long twoHoursInMilliseconds = 2L * 60 * 60 * 1000;
        long timePassed = new Date().getTime() - lastBingoTime.getTime();
        return timePassed > twoHoursInMilliseconds;
    }

    public String getGameId() { return gameId; }
    public List<Integer> getDrawnNumbers() { return drawnNumbers; }
    public List<PlayerResult> getBingoPlayers() { return bingoPlayers; }
    public List<PlayerResult> getReachPlayers() { return reachPlayers; }
}
