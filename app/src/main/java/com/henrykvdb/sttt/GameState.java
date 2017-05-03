package com.henrykvdb.sttt;

import android.os.Handler;
import com.flaghacker.uttt.bots.RandomBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static com.henrykvdb.sttt.GameService.Source.AI;
import static com.henrykvdb.sttt.GameService.Source.Bluetooth;
import static com.henrykvdb.sttt.GameService.Source.Local;

public class GameState implements Serializable
{
	private static final long serialVersionUID = -3051602110955747927L;

	private final Players players;
	private final LinkedList<Board> boards;
	private final Bot extraBot;
	private final Handler btHandler;

	private GameState(Players players, LinkedList<Board> boards, Bot extraBot, Handler btHandler)
	{
		this.players = players;
		this.boards = boards;
		this.extraBot = extraBot;
		this.btHandler = btHandler;
	}

	static class Players
	{
		public final GameService.Source first;
		public final GameService.Source second;

		public Players(GameService.Source first, GameService.Source second)
		{
			this.first = first;
			this.second = second;
		}

		public Players swap()
		{
			return new Players(second, first);
		}

		public boolean contains(GameService.Source source)
		{
			return first == source || second == source;
		}
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public static class Builder
	{
		private Players players = new Players(Local, Local);
		private LinkedList<Board> boards = new LinkedList<>(Collections.singletonList(new Board()));
		private boolean swapped = new Random().nextBoolean();
		private Bot extraBot = new RandomBot();
		private Handler btHandler;

		public GameState build()
		{
			return new GameState(swapped ? players.swap() : players, boards, extraBot, btHandler);
		}

		public Builder boards(List<Board> boards)
		{
			this.boards = new LinkedList<>(boards);
			return this;
		}

		public Builder board(Board board)
		{
			return this.boards(Collections.singletonList(board));
		}

		public Builder swapped(boolean swapped)
		{
			this.swapped = swapped;
			return this;
		}

		public Builder ai(Bot extraBot)
		{
			this.extraBot = extraBot;
			players = new Players(Local, AI);
			return this;
		}

		public Builder players(Players players)
		{
			this.players = players;
			return this;
		}

		public Builder bt(Handler btHandler)
		{
			this.btHandler = btHandler;
			players = new Players(Local, Bluetooth);
			return this;
		}

		public Builder gs(GameState gs)
		{
			this.players = gs.players();
			this.boards = gs.boards();
			this.swapped = false;
			this.extraBot = gs.extraBot();
			this.btHandler = gs.btHandler();
			return this;
		}
	}

	public Players players()
	{
		return players;
	}

	public LinkedList<Board> boards()
	{
		return boards;
	}

	public void pushBoard(Board board)
	{
		this.boards.push(board);
	}

	public void popBoard()
	{
		this.boards.pop();
	}

	public Board board()
	{
		return boards.peek();
	}

	public Bot extraBot()
	{
		return extraBot;
	}

	public Handler btHandler()
	{
		return btHandler;
	}

	public boolean isBluetooth()
	{
		return players.contains(Bluetooth);
	}

	public boolean isAi()
	{
		return players.contains(AI);
	}

	public boolean isLocal()
	{
		return players.first == Local && players.second == Local;
	}
}
