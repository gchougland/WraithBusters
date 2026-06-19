# Wraith Busters

**A Hytale mod by Hexvane**

Wraith Busters is a multiplayer horror hide-and-seek set inside a haunted mansion. One team plays as living explorers while the other stalks the halls as ghosts, and every round turns into a race between solving puzzles and surviving the hunt.

**Work in progress.** This mod is still in active development. Features, balance, and content may change between updates.

**The Hytale Modjam:** Entry for the Horror Minigames category. [hytalemodjam.com](https://hytalemodjam.com/)

## How to Play

Use `/wb` or `/wraithbusters` followed by a subcommand:

| Command | What it does |
| --- | --- |
| `/wb start` | Create a new game lobby in the haunted mansion |
| `/wb join` | Join the open lobby |
| `/wb ready` | Toggle ready in the lobby |
| `/wb leave` | Leave the current game |

One player starts a lobby, others join, everyone readies up, and the countdown begins automatically.

## The Setup

Gather friends in a lobby, ready up, and the hunt begins. Most players are humans scattered through the mansion. One or more players become ghosts, invisible to the living and free to move through walls and secret passages. The mansion stays dark, tense, and full of locked doors between you and the final goal.

## Playing as a Human

Your job is to work together, explore room by room, and push deeper into the mansion before time runs out or your team is picked off.

- **Solve puzzles** to earn keys and open the next door. Puzzles range from lighting candles in the right order, preparing offerings in the kitchen or laboratory, chasing mice for cheese in the garden, returning colored books to the library shelves, and more.
- **Find keys** hidden around each room and match them to the right locked doors.
- **Reach the attic** and use the exorcism table to banish the spirit. If you succeed, the humans win.

Stay alert. When a human falls, they are out for the rest of the round and can only watch from the shadows.

## Playing as a Ghost

You cannot be seen by humans, but you are not helpless. Collect spirit energy floating through the mansion to fuel your powers, then strike from the darkness.

- **Haunt objects** around the mansion: hurl plates, ignite ghost fire, swing sword statues, unleash snapdragons, or send out bee swarms when a human is nearby.
- **Phase through doors** using ghost portals that only you can see and use.
- **Hunt the living** until no humans remain, or simply run out the clock.

If every human is eliminated or the timer hits zero, the ghosts win.

## The Haunted Mansion

The included mansion is a full adventure map with many rooms to discover, including the study, kitchen, dining room, ballroom, garden, altar, laboratory, cellar, bedroom, library, and attic. Each area holds its own secrets, keys, and puzzles standing between the humans and victory.

## After the Round

When a round ends, everyone sees who won and can jump straight into another match or head home. Win or lose, the mansion is ready for the next hunt.

## Library Puzzle Setup (Builders)

The library room uses the `library_books` puzzle. Before it can run in a round, place and mark the arena once in setup mode:

1. `/wb setup enter mansion_v1`
2. Mark the library room door: `/wb setup mark Library`
3. Place four puzzle bookshelves (Blue, Green, Orange, Red) from the WraithBusters creative category
4. Look at each shelf and run `/wb setup mark bookshelf --extra blue` (or green, orange, red)
5. Mark four floor spots where book pickups should spawn: `/wb setup mark book_spawn` (repeat four times)
6. Save: `/wb setup save mansion_v1`

During a round, when the library is the active puzzle room, colored book pickups spawn at the marked spots (shuffled each time). Humans gather the books and return each one to the matching-color shelf.
