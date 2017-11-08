package com.andreas.rockpaperscissors.model;

import com.andreas.rockpaperscissors.controller.AppController;
import com.andreas.rockpaperscissors.util.Constants;
import com.andreas.rockpaperscissors.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Game implements NetObserver {
    private List<Player> playerList = new ArrayList<>();
    private AppController appController = AppController.getInstance();
    private ArrayList<GameObserver> gameObservers = new ArrayList<>();
    private GameRound gameRound = null;
    private int totalScore = 0;
    private final Player player;

    public Game(String playerName, String uniqueName) {
        this.player = new Player(playerName, uniqueName);
    }

    public void addGameObserver(GameObserver gameObserver) {
        gameObservers.add(gameObserver);
    }

    public String getUniqueName() {
        return player.getUniqueName();
    }
    public String getDisplayName() { return player.getDisplayName(); }

    @Override
    public void playerInfo(Player player) {
        Logger.log("Game got player info: " + player.getUniqueName());
        addPlayerIfNew(player);
    }

    @Override
    public void playerPlaysCommand(Player player, PlayCommand playCommand) {
        if (gameRound == null)
            gameRound = new GameRound(playerList, this);
        gameRound.playerPlaysCommand(player, playCommand);
    }

    @Override
    public void chatMessage(String message) {
        for (GameObserver gameObserver : gameObservers)
            gameObserver.chatMessage(message);
    }


    private void addPlayerIfNew(Player player) {
        if (!playerList.contains(player)) {
            playerList.add(player);
            notifyPlayerJoinedTheGame(player.getDisplayName());
            appController.sendPlayerInfo(null);
        }
    }

    private void notifyPlayerJoinedTheGame(String newPlayer) {
        List<String> playerNames = new ArrayList<>();
        for (Player player : playerList)
            playerNames.add(player.getDisplayName());
        for (GameObserver gameObserver : gameObservers) {
            gameObserver.playerJoinedTheGame(newPlayer);
            gameObserver.allPlayers(playerNames);
        }
    }

    private void notifyPlayerLeftTheGame(Player lostPlayer) {
        List<String> playerNames = new ArrayList<>();
        for (Player player : playerList)
            playerNames.add(player.getDisplayName());
        for (GameObserver gameObserver : gameObservers) {
            gameObserver.playerLeftTheGame(lostPlayer.getDisplayName());
            gameObserver.allPlayers(playerNames);
        }
    }


    @Override
    public void playerNotResponding(String uniqueName) {
        Player deadPlayer = new Player("Unknown", uniqueName);
        for (int i = 0; i < playerList.size(); i++) {
            Player player = playerList.get(i);
            if (deadPlayer.equals(player)) {
                playerList.remove(player);
                if (gameRound != null)
                    gameRound.removePlayer(deadPlayer);
                notifyPlayerLeftTheGame(deadPlayer);
                i--;
            }
        }
    }

    void roundCompleted(GameRound gameRound) {
        if (gameRound.isDraw())
            notifyDraw();
        else if (gameRound.isWonBy(player))
            notifyVictory(gameRound.scoreForWinner(player));
        else
            notifyLoss();
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                notifyNewRound();
            }
        }, Constants.WAIT_BEFORE_NEXT_ROUND_MS);
    }

    private void notifyNewRound() {
        for (GameObserver gameObserver : gameObservers)
            gameObserver.newRound(totalScore);
        this.gameRound = null;
    }

    private void notifyDraw() {
        for (GameObserver gameObserver : gameObservers)
            gameObserver.draw();
    }

    private void notifyLoss() {
        for (GameObserver gameObserver : gameObservers)
            gameObserver.loss();
    }

    private void notifyVictory(int roundScore) {
        totalScore += roundScore;
        for (GameObserver gameObserver : gameObservers)
            gameObserver.victory(roundScore, totalScore);
    }

    public Player getPlayer() {
        return this.player;
    }
}
