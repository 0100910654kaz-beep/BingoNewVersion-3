package servlet;

import java.io.Serializable;
import java.util.Date;

public class PlayerResult implements Serializable, Comparable<PlayerResult> {
    private static final long serialVersionUID = 1L;

    private String playerName;          // プレイヤー名
    private Date reachedTime;           // 達成した日時（ミリ秒単位まで正確に記録）
    private int drawnNumberAtBingo;     // 【新機能】ビンゴ（またはリーチ）した瞬間の当選番号

    public PlayerResult(String playerName, Date reachedTime, int drawnNumberAtBingo) {
        this.playerName = playerName;
        this.reachedTime = reachedTime;
        this.drawnNumberAtBingo = drawnNumberAtBingo;
    }

    public String getPlayerName() { return playerName; }
    public Date getReachedTime() { return reachedTime; }
    public int getDrawnNumberAtBingo() { return drawnNumberAtBingo; } // ビンゴ番号を取り出す部品

    // 最新の達成者ほど「上（リストの先頭）」に並び替えるためのロジック
    // （※BingoGame側で先頭に追加する形に補強したため、このメソッドは安全のために残してあります）
    @Override
    public int compareTo(PlayerResult other) {
        return other.getReachedTime().compareTo(this.reachedTime);
    }
}