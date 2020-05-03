//My bots name is PlsLetMeInPlatinum ~ On a side note, thanks for moving the due date to Sunday because it allowed me to be able to rise into platinum. 

// This Java API uses camelCase instead of the snake_case as documented in the API docs.
//     Otherwise the names of methods are consistent.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import hlt.*;

import java.util.Random;

public class MyBot {

	// The maximum time that a ship will spawn ships
	static int timeToStopSpawning;
	// The maximum amount of halite where a ship will not mine it
	static int minHaliteOnSquare;
	// The amount of halite a ship will return when reached
	static int haliteCountToReturn = 950;
	// The maximum amount of dropoffs, set to 5000 so that it will always have
	// enough unless specified under certain conditions
	static int maxNumOfDropoffs = 5000;
	// The number of dropoffs currently spawned
	static int numOfDropoffs = 0;
	// The minimum distance away a ship must be before converting into a dropoff
	static int otherDropoffDist = 10;
	// The amount of time that is subtracted from the total time where all of the
	// ships return
	static int returnCount = 50;
	// The number of spawned
	static int cNumOfShips = 0;
	// The total halite available on the map at the start
	static double totalStartingHalite = 0;
	// The percent of halite mined on the map to stop spawning ships
	static double percentToStop;
	// The maximum distance away from the ship in which to look when evaluating the
	// average area around the ship
	static int distToLook = 5;
	// The difference that is needed to be able to spawn a dropoff, when this amount
	// of new ships are spawned a new dropoff is able to be made.
	static int shipDifference = 17;
	// HashMap dictionary which stores the paths for the ships, reduces amount of
	// time I am spent creating paths for the ships by actually being able to store
	// them, and results in me not having to calculate the most optimal position to
	// move to every turn.
	static HashMap<EntityId, ArrayList<Position>> moveDict = new HashMap<EntityId, ArrayList<Position>>();

	public static void main(final String[] args) {
		final long rngSeed;
		if (args.length > 1) {
			rngSeed = Integer.parseInt(args[1]);
		} else {
			rngSeed = System.nanoTime();
		}
		final Random rng = new Random(rngSeed);

		Game game = new Game();

		// The time to stop spawning ships is set at 70% of the time elapsed, will stop
		// spawning ships after 70% of the time has elapsed.
		timeToStopSpawning = (int) (Constants.MAX_TURNS * 0.7);
		// The percentage of the map mined where ships will stop being spawned.
		percentToStop = 1.0;
		// If the map size is less than 48 and its a 4 player game or it is just a 32
		// size map the number of dropoffs will be capped to 2
		if ((game.gameMap.width < 48 && game.players.size() == 4) || game.gameMap.width == 32)
			maxNumOfDropoffs = 2;

		// Gets you the total amount of halite available on the map at the start
		totalStartingHalite = haliteOnMap(game.gameMap);

		game.ready("MyJavaBot");

		Log.log("Successfully created bot! My Player ID is " + game.myId + ". Bot rng seed is " + rngSeed + ".");

		for (;;) {
			game.updateFrame();
			final Player me = game.me;
			final GameMap gameMap = game.gameMap;
			final ArrayList<Command> commandQueue = new ArrayList<>();

			// The positions that are presently occupied by my ships, stops collisions with
			// my ships
			HashSet<Position> occupiedPositions = new HashSet<Position>();

			// The positions claimed by my ships to mine, only positions that have a halite
			// amount greater than minHaliteOnSquare, works to help optimize my path finder
			// for mining halite
			HashSet<Position> takenPositions = new HashSet<Position>();

			// The ships that have already been issued a command this turn, used to
			// prioritize stay still and swapping, stops me from issuing more than one
			// command to my ships
			HashSet<EntityId> takenShips = new HashSet<EntityId>();

			// The amount of halite that is currently present on the map at this turn
			double halitePresentInTurn = 0;

			// Boolean that makes it so that only one dropoff is spawned per turn
			boolean alreadySpawned = false;

			// gets you the halite available on the map at the start of the turn
			halitePresentInTurn = haliteOnMap(gameMap);

			// Will get you the percent of halite that has been collected from the map
			double percentCollected = (totalStartingHalite - halitePresentInTurn) / totalStartingHalite;

			// Calculates the average halite on square, but halves it, chosen to half it
			// because I found this works the best with my bot
			int averageHaliteOnSquare = ((int) ((halitePresentInTurn / (gameMap.width * gameMap.height))) / 2);

			// setting the minimum amount of halite at which a ship will not mine a square
			// based on half of the average amount of halite per square.
			minHaliteOnSquare = averageHaliteOnSquare;

			// Will spawn a shipyard if the percentage collected is less than the stopping
			// percentage calculated, if the turn number is within the maximum set turn
			// number, if the shipyard is not occupied, if the player has 1000 halite, and
			// if there are no incoming ships to dropoff halite with a distance of 1 away
			if (safeSpawn(game, me) && me.ships.size() < 100 && me.halite >= 1000
					&& !game.gameMap.at(me.shipyard.position).isOccupied()) {
				commandQueue.add(me.shipyard.spawn());
				cNumOfShips++;
			}
			// Loops through all the ships, marking the position they are at as occupied and
			// marking positions they want to move to that have a halite amount greater than
			// minHaliteOnSquare as taken, it will also make ships stay still if they meet
			// the conditions, done to prioritize ships staying still
			for (Ship ship : me.ships.values()) {
				// Adds each ship postion to the HashSet occupiedPositions, done for marking
				// occupied positions and stopping collisions.
				occupiedPositions.add(ship.position);

				// If the ship has a path in the path dictionary it will loop through each
				// position in it and if it has a halite amount greater than minHaliteOnSquare
				// it will add it to the takenPositions, my way of reserving halite spots
				if (moveDict.containsKey(ship.id) && moveDict.get(ship.id).size() > 0) {
					for (Position pos : moveDict.get(ship.id)) {
						if (gameMap.at(pos).halite > minHaliteOnSquare)
							takenPositions.add(pos);
					}
				}
				// If the position the ship is on has halite > minHaliteOnSquare and it is not
				// full, it will contniue to mine, also if it doesnt have enough to travel it
				// will also stay still Will add the ship to taken ships so it isn't given
				// another position to move to.
				if ((game.gameMap.at(ship.position).halite > minHaliteOnSquare && ship.halite != 1000)
						|| ship.halite < 0.1 * game.gameMap.at(ship.position).halite) {
					// Marks ship as taken and orders it to stay still
					commandQueue.add(ship.stayStill());
					takenShips.add(ship.id);
				}
			}
			// My swapping ships function, only works when they are not trying to return
			// home at the end of the game
			if (game.turnNumber < Constants.MAX_TURNS - returnCount) {
				for (Ship ship : me.ships.values()) {
					// If the ship has already been issued a command or has no path in the
					// dictionary it will skip it
					if (takenShips.contains(ship.id) || !moveDict.containsKey(ship.id)
							|| moveDict.get(ship.id).size() == 0)
						continue;
					// first checks to see if the ships path is blocked by one of my ships, if it is
					// it moves onto other conditions
					if (occupiedPositions.contains(moveDict.get(ship.id).get(0))) {

						// Next position in path for ships move dictionary
						Position wantingToMove = moveDict.get(ship.id).get(0);

						// The ship at the next position in ship's move dictionary
						Ship shipThere = gameMap.at(wantingToMove).ship;

						// If the ship blocking the current ships path is heading to the current ship's
						// position or it has no path in the dictionary, the two will swap with
						// each other
						if (!takenShips.contains(shipThere.id)
								&& (!moveDict.containsKey(shipThere.id) || moveDict.get(shipThere.id).size() == 0
										|| moveDict.get(shipThere.id).get(0) == ship.position)) {
							moveDict.get(ship.id).remove(0);
							if (moveDict.containsKey(shipThere.id) && moveDict.get(shipThere.id).size() > 0
									&& moveDict.get(shipThere.id).get(0) == ship.position) {
								moveDict.get(shipThere.id).remove(0);
							}
							// Marks the ships as taken, then sets the ships to move to each others
							// positions
							takenShips.add(ship.id);
							takenShips.add(shipThere.id);
							Direction directToHead = gameMap.getUnsafeMoves(ship.position, wantingToMove).get(0);
							commandQueue.add(ship.move(directToHead));
							commandQueue.add(shipThere.move(directToHead.invertDirection()));
						}
					}
				}
			}
			// My final loop through ships, makes a path home if it is the end of the game,
			// converts a ship to a dropoff if conditions are met,
			// makes a path for ships if they have no path, assigns a path home if they have
			// a halite count greater than haliteCountToReturn (950), makes a new path if
			// their present one is blocked and they did not swap ships, or just ultimately
			// ends up moving to the next position if nothing else is met.
			for (Ship ship : me.ships.values()) {
				// the closest dropoff to that ship
				Position dropoff = closestDropoff(game, me, ship);

				// If ship has already been issued a command skips it
				if (takenShips.contains(ship.id)) {
					continue;
					// If shipDiffernece (17) amount of new ships have been made, there is enough
					// halite for a dropoff and a ship to be spawned, if the dropoff will be an
					// appropriate distance away from the other dropoffs, and the average amount of
					// halite in that area is greater than 200 per mapcell, the ship will be
					// converted into a dropoff
				} else if (cNumOfShips - (shipDifference * numOfDropoffs) >= shipDifference && me.halite >= 5000
						&& !alreadySpawned && gameMap.calculateDistance(ship.position, dropoff) >= otherDropoffDist
						&& numOfDropoffs < maxNumOfDropoffs && averageHaliteInArea(gameMap, ship.position) > 200) {
					numOfDropoffs++;
					alreadySpawned = true;
					commandQueue.add(ship.makeDropoff());
					// If the game turn number falls within the range of the time to return, set a
					// path home and continue following it
				} else if (game.turnNumber > (Constants.MAX_TURNS - returnCount)) {
					// If the ship is right next to the dropoff just move into it, does this so
					// that all of them get into the shipyard in the fastest amount of time
					if (gameMap.calculateDistance(ship.position, dropoff) == 1) {
						commandQueue.add(ship.move(gameMap.getUnsafeMoves(ship.position, dropoff).get(0)));
					} else {
						// If no path or the path isnt to the closet dropoff make one to the dropoff
						if (!moveDict.containsKey(ship.id) || moveDict.get(ship.id).size() == 0
								|| !moveDict.get(ship.id).get(moveDict.get(ship.id).size() - 1).equals(dropoff))
							makePath(gameMap, ship, moveDict, occupiedPositions, takenPositions, dropoff, commandQueue,
									true);
						// Makes a move with the current path in the dictionary
						makeMove(gameMap, ship, moveDict, occupiedPositions, commandQueue, dropoff);
					}
					// If there is no path in the dictionary create one
				} else if (!moveDict.containsKey(ship.id) || moveDict.get(ship.id).size() == 0) {
					// Function that makes a new path for the ship
					makePath(gameMap, ship, moveDict, occupiedPositions, takenPositions, dropoff, commandQueue, false);
					// Function that makes a move with the current path in the dictionary
					makeMove(gameMap, ship, moveDict, occupiedPositions, commandQueue, dropoff);

					// If the ship has enough halite to head home and it is not already heading home
					// it will make a path back
				} else if (ship.halite > haliteCountToReturn
						&& moveDict.get(ship.id).get(moveDict.get(ship.id).size() - 1) != dropoff) {
					// Makes a new path for the ship
					makePath(gameMap, ship, moveDict, occupiedPositions, takenPositions, dropoff, commandQueue, false);
					// Makes a move for the ship
					makeMove(gameMap, ship, moveDict, occupiedPositions, commandQueue, dropoff);

					// If the ships next position in the dictionary is occupied by one of my ships
					// and it has not swapped with it make a new path either home if it has enough
					// halite to return or just make a new mining path
				} else if (occupiedPositions.contains(moveDict.get(ship.id).get(0))) {
					// Creates the new path
					makePath(gameMap, ship, moveDict, occupiedPositions, takenPositions, dropoff, commandQueue, false);
					// Makes a move with the new path
					makeMove(gameMap, ship, moveDict, occupiedPositions, commandQueue, dropoff);
				} else {
					// makes a move if none of the other conditions are met
					makeMove(gameMap, ship, moveDict, occupiedPositions, commandQueue, dropoff);
				}

			}
			game.endTurn(commandQueue);
		}
	}

	// Calculates the halite on the map on that present turn
	public static int haliteOnMap(GameMap gameMap) {
		int output = 0;
		for (int row = 0; row < gameMap.height; row++) {
			for (int col = 0; col < gameMap.width; col++) {
				output += gameMap.at(new Position(col, row)).halite;
			}
		}
		return output;
	}

	// Master path maker for all the ships, will assign a path for each ship to the
	// dictionary based on its present conditions
	public static void makePath(GameMap gameMap, Ship ship, HashMap<EntityId, ArrayList<Position>> moveDict,
			HashSet<Position> occupiedPositions, HashSet<Position> takenPositions, Position dropoff,
			ArrayList<Command> commandQueue, boolean timeToHeadHome) {
		// The path that will be added to the dictionary for the ship
		ArrayList<Position> pathThere;

		// If the ship has enough halite to return or it is the end of the game and
		// should head home create a path home
		if (ship.halite > haliteCountToReturn || timeToHeadHome) {
			// Calls the pathHome function which will create a path using properties similar
			// to naive navigate and will then remove the first one in the list, which will
			// be the current position the ship is on
			pathThere = pathHome(gameMap, ship, dropoff);
			pathThere.remove(0);
		} else {
			// If the ship presently has a path in the dictionary, it will remove any
			// positions in the reserved positions that the ship has reserved, done so when
			// making a path it can use those positions again instead of seeing them as
			// reserved
			if (moveDict.containsKey(ship.id) && moveDict.get(ship.id).size() > 0) {
				for (Position pos : moveDict.get(ship.id)) {
					if (takenPositions.contains(pos))
						takenPositions.remove(pos);
				}
			}
			// Calls the halitePath function, which builds the path for the ship to mine
			pathThere = halitePath(gameMap, ship, dropoff, occupiedPositions, takenPositions);
			if (pathThere == null) {
				// If it failed to make a path, it will just return an empty path so it can
				// retry next turn
				pathThere = new ArrayList<Position>();
			} else {
				// Removes the first position in the list, which would be the ships current
				// position, then for each position in the list if it has a halite amount
				// greater than the minimum amount of halite needed to mine it will mark it as
				// reserved
				pathThere.remove(0);
				for (Position pos : pathThere) {
					if (gameMap.at(pos).halite > minHaliteOnSquare) {
						takenPositions.add(pos);
					}
				}
			}
		}
		// If there is not a path already present it will place a new path in the
		// dictionary with the ship id
		if (!moveDict.containsKey(ship.id))
			moveDict.put(ship.id, pathThere);
		else
			// If there is already a path present the path will be replaced with the new
			// path
			moveDict.replace(ship.id, pathThere);

	}

	// The master moving function for my bot, choses for the bot to either move to
	// the next position in its path stored in the dictionary or to stay still
	public static void makeMove(GameMap gameMap, Ship ship, HashMap<EntityId, ArrayList<Position>> moveDict,
			HashSet<Position> occupiedPositions, ArrayList<Command> commandQueue, Position dropoff) {
		// If the path is empty for the ship stay still
		if (moveDict.get(ship.id).size() == 0) {
			commandQueue.add(ship.stayStill());
			return;
		}
		// The position that is next in the dictionary for the ship
		Position posMovingTo = moveDict.get(ship.id).get(0);

		// If the next position is occupied by one of my ships, stay still, final place
		// guard against collisions
		if (occupiedPositions.contains(posMovingTo))
			commandQueue.add(ship.stayStill());
		else {
			// Finds the direction needed for the ship to move to get to the next position
			// in its path, marks its new position as occupied and its present one as
			// unoccupied, and then issues the command to move there
			Direction directToHead = gameMap.getUnsafeMoves(ship.position, posMovingTo).get(0);
			moveDict.get(ship.id).remove(0);
			occupiedPositions.remove(ship.position);
			occupiedPositions.add(posMovingTo);
			commandQueue.add(ship.move(directToHead));
		}

	}

	// Pathing function home, has properties similar to naive navigate by just
	// finding the quickest path home disregarding everything else, doesnt look at
	// if each cell is occupied or not. Helps make the bot more effiicent as only
	// spends one turn making the entire path home, instead of having to calculate
	// each turn which position and direction to head to, it is just following its
	// path and pausing if its occupied.
	public static ArrayList<Position> pathHome(GameMap gameMap, Ship ship, Position dropoff) {
		ArrayList<Position> output = new ArrayList<Position>();
		Position current = ship.position;
		while (!current.equals(dropoff)) {
			// Makes path by just adding a position on the way to the dropoff until it
			// reaches the dropoff, like a depth first search but its moving towards the
			// dropoff
			output.add(current);
			current = gameMap.normalize(current.directionalOffset(gameMap.getUnsafeMoves(current, dropoff).get(0)));
		}
		output.add(dropoff);
		return output;
	}

	// calculates the average halite in the area with a breadthfirst serach moving
	// outwards until the current mapcell is a certain distance (5) away from the
	// starting point
	public static int averageHaliteInArea(GameMap gameMap, Position starting) {
		int output = 0;
		int cellsVisited = 0;
		ArrayList<Position> notVisited = new ArrayList<Position>();
		notVisited.add(starting);
		HashSet<Position> visited = new HashSet<Position>();
		while (true) {
			Position current = notVisited.get(0);
			cellsVisited++;
			output += gameMap.at(current).halite;
			notVisited.remove(0);
			visited.add(current);
			if (gameMap.calculateDistance(current, starting) > distToLook) {
				break;
			}
			for (Position pos : current.getSurroundingCardinals()) {
				if (!notVisited.contains(pos) && !visited.contains(pos))
					notVisited.add(pos);
			}
		}
		// returns the total amount of halite in that area divided by the number of
		// cells visited
		return output / cellsVisited;
	}

	// Creates a list of positions from a given dictionary of positions that record
	// their parent and a final position to build it from. Basically creates the
	// path to follow created from the breadthfirst search
	public static ArrayList<Position> reconstructPath(HashMap<Position, Position> cameFrom, Position current) {
		ArrayList<Position> totalPath = new ArrayList<Position>();
		totalPath.add(current);
		while (cameFrom.containsKey(current)) {
			current = cameFrom.get(current);
			totalPath.add(0, current);
		}
		return totalPath;
	}

	// My function for building the path for the ships to follow when mining, is in
	// its basis a breadth first search, where it will build a path of positions for
	// the ship to follow that will result in it being full in the shortest amount
	// of time. I chose to use a breadth first search here so that it will be able
	// to build the path in the shortest amount of time, as it will work outwords
	// instead of just focusing on a specific branch. Helps to make actual bot more
	// efficient with instead of having to check everytime to find the best position
	// possible for every ship, this will find a path of best positions for the ship
	// to follow only when the path is no longer good or its been fulfilled.
	public static ArrayList<Position> halitePath(GameMap gameMap, Ship ship, Position closestDropoff,
			HashSet<Position> occupiedPositions, HashSet<Position> takenPositions) {
		ArrayList<Position> frontier = new ArrayList<Position>();
		HashSet<Position> visited = new HashSet<Position>();
		HashMap<Position, Position> cameFrom = new HashMap<Position, Position>();
		HashMap<Position, Integer> hCount = new HashMap<Position, Integer>();
		frontier.add(ship.position);
		hCount.put(ship.position, 0);
		while (frontier.size() > 0) {
			Position current = frontier.get(0);
			frontier.remove(current);
			// If the current Positions halite count is greater than the amount needed to
			// fill the ship, return the path from the current position, basically if that
			// path will fill the ship up in terms of halite
			if (hCount.get(current) >= haliteCountToReturn - ship.halite) {
				return reconstructPath(cameFrom, current);
			}
			visited.add(current);
			boolean added = false;
			// Looks at its neighbors, adding unvisited neighbors to the frontier and making
			// the neighbors halite count be the current + theirs, subtracts the amount that
			// is on the min halite square from each so that it doesn't take that into
			// account. Takes into account reserved positions as well and will not pick a
			// position if it is reserved
			for (Position neighbor : current.getSurroundingCardinals()) {
				neighbor = gameMap.normalize(neighbor);
				if (visited.contains(neighbor) || occupiedPositions.contains(neighbor)
						|| gameMap.at(neighbor).isOccupied() || takenPositions.contains(neighbor)) {
					continue;
				} else
					added = true;
				int tentativeHCount = hCount.get(current) + gameMap.at(neighbor).halite;
				if (gameMap.at(neighbor).halite < minHaliteOnSquare) {
					tentativeHCount -= gameMap.at(neighbor).halite;
				} else {
					tentativeHCount -= minHaliteOnSquare;
				}
				if (!frontier.contains(neighbor)) {
					frontier.add(neighbor);
				} else if (hCount.get(neighbor) > tentativeHCount) {
					continue;
				}
				cameFrom.put(neighbor, current);
				hCount.put(neighbor, tentativeHCount);
			}
			// if no neighbors were added, re runs evaluating the neighbors but this time
			// doesn't take into account whether or not the neighbors are reserved by other
			// ships, does this so that it can use those positions as just traveling points.
			if (!added) {
				for (Position neighbor : current.getSurroundingCardinals()) {
					neighbor = gameMap.normalize(neighbor);
					if (visited.contains(neighbor) || occupiedPositions.contains(neighbor)
							|| gameMap.at(neighbor).isOccupied()) {
						continue;
					}
					int tentativeHCount = gameMap.at(neighbor).halite;

					if (!frontier.contains(neighbor)) {
						frontier.add(neighbor);
					} else if (hCount.get(neighbor) > tentativeHCount) {
						continue;
					}
					cameFrom.put(neighbor, current);
					hCount.put(neighbor, tentativeHCount);
				}
			}
		}
		return null;
	}

	// Finds the closest dropoff to the given ship based on the current dropoffs the
	// player has and the shipyard position.
	public static Position closestDropoff(Game cGame, Player me, Ship ship) {
		ArrayList<Position> listOfDropoffs = new ArrayList<Position>();
		listOfDropoffs.add(me.shipyard.position);
		for (Dropoff d : me.dropoffs.values()) {
			listOfDropoffs.add(d.position);
		}
		Position bestDropoff = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Position p : listOfDropoffs) {
			if (cGame.gameMap.calculateDistance(ship.position, p) < bestDistance) {
				bestDistance = cGame.gameMap.calculateDistance(ship.position, p);
				bestDropoff = p;
			}
		}
		return bestDropoff;
	}

	// Determines if it is safe for a ship to be spawned based on if there are any
	// incoming ships that need to dropoff halite, says it is unsafe if there is a
	// ship one space away from the shipyard trying to dropoff halite
	public static boolean safeSpawn(Game cGame, Player p) {
		for (Position pos : p.shipyard.position.getSurroundingCardinals()) {
			MapCell nodey = cGame.gameMap.at(pos);
			if (nodey.isOccupied() && nodey.ship.halite > 500) {
				return false;
			}
		}
		return true;
	}
}
