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

    // 🚀 各プレイヤーのカードデータをサーバー側でも管理・自動スキャンするための箱
    private ConcurrentHashMap<String, List<List<String>>> playerCards = new ConcurrentHashMap<>();
    // 🚀 各プレイヤーの「待ち数字（ビンゴする番号）」を記憶する箱
    private ConcurrentHashMap<String, List<String>> playerWaitNumbers = new ConcurrentHashMap<>();

    public BingoGame(String gameId, int validDays) {
        this.gameId = gameId;
        this.drawnNumbers = new CopyOnWriteArrayList<>();
        this.bingoPlayers = new CopyOnWriteArrayList<>();
        this.reachPlayers = new CopyOnWriteArrayList<>();
        this.allPlayers = new CopyOnWriteArrayList<>();
        this.lastBingoTime = new Date();
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, validDays);
        this.expireTime = cal.getTime();
    }

    // プレイヤーをゲームに参加登録する
    public synchronized String registerPlayer(String name) {
        if (name == null || name.trim().isEmpty()) {
            char suffix = (char) ('A' + (anonymousCount % 26));
            name = "Player-" + suffix;
            if (anonymousCount >= 26) {
                name += (anonymousCount / 26 + 1);
            }
            anonymousCount++;
        }
        
        String trimmedName = name.trim();
        if (!allPlayers.contains(trimmedName)) {
            allPlayers.add(trimmedName);
        }
        return trimmedName;
    }

    // 🚀 サーブレットで生成されたカードをサーバーに登録し、その場で自動判定を走らせる
    public void setPlayerCard(String name, List<List<String>> card) {
        playerCards.put(name, card);
        checkAutoReachAndBingo(name); // 参加した瞬間の初期チェック（FREEマスがあるため）
    }

    public List<List<String>> getPlayerCard(String name) {
        return playerCards.get(name);
    }

    // 🚀 【核心】全自動でリーチ・ビンゴ・待ち数字を割り出す大山さん専用ロジック
    public void checkAutoReachAndBingo(String name) {
        List<List<String>> card = playerCards.get(name);
        if (card == null) return;

        // すでにビンゴしている人はスキップ
        for (PlayerResult p : bingoPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }

        // 縦・横・斜めの全12ラインの「穴あき状況」をチェック
        List<List<String>> lines = new ArrayList<>();
        
        // 横5行
        for (int r = 0; r < 5; r++) {
            lines.add(card.get(r));
        }
        // 縦5列
        for (int c = 0; c < 5; c++) {
            List<String> col = new ArrayList<>();
            for (int r = 0; r < 5; r++) {
                col.add(card.get(r).get(c));
            }
            lines.add(col);
        }
        // 斜め（右下がり）
        List<String> slash1 = new ArrayList<>();
        for (int i = 0; i < 5; i++) slash1.add(card.get(i).get(i));
        lines.add(slash1);
        
        // 斜め（右上がり）
        List<String> slash2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) slash2.add(card.get(i).get(4 - i));
        lines.add(slash2);

        boolean isBingo = false;
        List<String> waitNumbers = new ArrayList<>(); // リーチ時の待ち数字リスト

        // 12ラインを1本ずつ精査
        for (List<String> line : lines) {
            List<String> missingNumbers = new ArrayList<>();
            for (String numStr : line) {
                int num = Integer.parseInt(numStr);
                // まだ当選していない、かつFREE(0)でもない数字を「穴あいてないリスト」に入れる
                if (num != 0 && !drawnNumbers.contains(num)) {
                    missingNumbers.add(numStr);
                }
            }

            // 【ビンゴ判定】そのラインの未当選数字が 0 個なら一発ビンゴ！
            if (missingNumbers.size() == 0) {
                isBingo = true;
                break;
            }
            // 【リーチ判定】そのラインの未当選数字が「あと1個」なら、それが待ち数字
            else if (missingNumbers.size() == 1) {
                String waitNum = missingNumbers.get(0);
                if (!waitNumbers.contains(waitNum)) {
                    waitNumbers.add(waitNum);
                }
            }
        }

        if (isBingo) {
            // 🎉 自動ビンゴ確定！
            addBingoPlayer(name);
            playerWaitNumbers.remove(name);
        } else if (!waitNumbers.isEmpty()) {
            // 🔥 自動リーチ確定！待ち数字を記憶
            playerWaitNumbers.put(name, waitNumbers);
            addReachPlayer(name);
        } else {
            // まだ何でもない状態ならリストから外す
            playerWaitNumbers.remove(name);
            removeReachPlayer(name);
        }
    }

    // 🚀 番号が引かれた時、全プレイヤーのカードを裏で一斉に自動スキャンする命令
    public void checkAllPlayers() {
        for (String name : allPlayers) {
            checkAutoReachAndBingo(name);
        }
    }

    // ビンゴ登録（自動判定から呼ばれる）
    private void addBingoPlayer(String name) {
        for (PlayerResult p : bingoPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        int currentDrawnNumber = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
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
    public List<String> getAllPlayers() { return allPlayers; }
    public int getPlayerCount() { return allPlayers.size(); }
    public Date getExpireTime() { return expireTime; }
}
