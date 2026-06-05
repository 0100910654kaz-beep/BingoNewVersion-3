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

    private String gameId;                             
    private List<Integer> drawnNumbers;                
    private List<PlayerResult> bingoPlayers;           
    private List<PlayerResult> reachPlayers;           
    private List<String> allPlayers;                   
    private Date expireTime;                           
    private Date lastBingoTime;                        
    private int anonymousCount = 0;                    

    private ConcurrentHashMap<String, List<List<String>>> playerCards = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<String>> playerWaitNumbers = new ConcurrentHashMap<>();

    public BingoGame(String gameId, int validDays) {
        // 💡 固定値 "88888888" の上書きを完全に撤廃し、渡された4桁の簡単なIDをそのまま記憶します
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

        checkAllPlayersStatus(chosen);
    }

    public void setPlayerCard(String name, List<List<String>> card) {
        if (name == null || card == null) return;
        playerCards.put(name, card);
        
        int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
        checkSinglePlayerStatus(name, lastNum);
    }

    private void checkAllPlayersStatus(int currentDrawnNumber) {
        for (String name : playerCards.keySet()) {
            checkSinglePlayerStatus(name, currentDrawnNumber);
        }
    }

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
        for (int r = 0; r < 5; r++) {
            List<String> line = new ArrayList<>();
            for (int c = 0; c < 5; c++) line.add(r + "," + c);
            lines.add(line);
        }
        for (int c = 0; c < 5; c++) {
            List<String> line = new ArrayList<>();
            for (int r = 0; r < 5; r++) line.add(r + "," + c);
            lines.add(line);
        }
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
                int c = Integer.parseInt(parts[1]); // 👈 以前のバグ修正部分も完全に保護
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

    private void addBingoPlayer(String name, int currentDrawnNumber) {
        for (PlayerResult p : bingoPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        Date now = new Date();
        bingoPlayers.add(0, new PlayerResult(name, now, currentDrawnNumber));
        this.lastBingoTime = now;
        removeReachPlayer(name);
    }

    private void addReachPlayer(String name) {
        for (PlayerResult p : reachPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        reachPlayers.add(0, new PlayerResult(name, new Date(), 0));
    }

    public void removeReachPlayer(String name) {
        reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
    }

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
