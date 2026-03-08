import { useEffect, useMemo, useRef, useState } from 'react'

type ArcadeGame = 'snake' | 'mines'
type Direction = 'up' | 'down' | 'left' | 'right'

const SNAKE_SIZE = 14
const SNAKE_CELL_COUNT = SNAKE_SIZE * SNAKE_SIZE
const SNAKE_TICK_MS = 170
const MINES_SIZE = 8
const MINES_COUNT = 10
const SNAKE_BEST_KEY = 'neon-horizon-web-snake-best'

type MineCell = {
  mine: boolean
  revealed: boolean
  flagged: boolean
  adjacent: number
}

function isOppositeDirection(current: Direction, next: Direction) {
  return (
    (current === 'up' && next === 'down') ||
    (current === 'down' && next === 'up') ||
    (current === 'left' && next === 'right') ||
    (current === 'right' && next === 'left')
  )
}

function buildInitialSnake() {
  const center = Math.floor(SNAKE_CELL_COUNT / 2 + SNAKE_SIZE / 2)
  return [center, center - 1, center - 2]
}

function createSnakeFood(occupied: number[]) {
  const occupiedSet = new Set(occupied)
  const candidates = Array.from({ length: SNAKE_CELL_COUNT }, (_, index) => index).filter(
    (index) => !occupiedSet.has(index)
  )
  return candidates[Math.floor(Math.random() * candidates.length)] ?? 0
}

function getNextSnakeHead(head: number, direction: Direction) {
  switch (direction) {
    case 'up':
      return head - SNAKE_SIZE
    case 'down':
      return head + SNAKE_SIZE
    case 'left':
      return head - 1
    case 'right':
      return head + 1
  }
}

function hitsSnakeWall(head: number, nextHead: number, direction: Direction) {
  if (direction === 'left') {
    return head % SNAKE_SIZE === 0
  }

  if (direction === 'right') {
    return head % SNAKE_SIZE === SNAKE_SIZE - 1
  }

  if (direction === 'up') {
    return nextHead < 0
  }

  return nextHead >= SNAKE_CELL_COUNT
}

function createMineBoard() {
  const board = Array.from({ length: MINES_SIZE * MINES_SIZE }, () => ({
    mine: false,
    revealed: false,
    flagged: false,
    adjacent: 0
  }))

  let minesPlaced = 0
  while (minesPlaced < MINES_COUNT) {
    const index = Math.floor(Math.random() * board.length)
    if (board[index].mine) continue
    board[index].mine = true
    minesPlaced += 1
  }

  for (let index = 0; index < board.length; index += 1) {
    if (board[index].mine) continue

    const row = Math.floor(index / MINES_SIZE)
    const column = index % MINES_SIZE
    let adjacent = 0

    for (let rowOffset = -1; rowOffset <= 1; rowOffset += 1) {
      for (let columnOffset = -1; columnOffset <= 1; columnOffset += 1) {
        if (rowOffset === 0 && columnOffset === 0) continue
        const nextRow = row + rowOffset
        const nextColumn = column + columnOffset
        if (
          nextRow < 0 ||
          nextRow >= MINES_SIZE ||
          nextColumn < 0 ||
          nextColumn >= MINES_SIZE
        ) {
          continue
        }
        if (board[nextRow * MINES_SIZE + nextColumn].mine) {
          adjacent += 1
        }
      }
    }

    board[index].adjacent = adjacent
  }

  return board
}

function revealSafeMineCells(board: MineCell[], startIndex: number) {
  const nextBoard = board.map((cell) => ({ ...cell }))
  const queue = [startIndex]
  const visited = new Set<number>()

  while (queue.length > 0) {
    const index = queue.shift()!
    if (visited.has(index)) continue
    visited.add(index)

    const cell = nextBoard[index]
    if (cell.flagged || cell.revealed) continue

    cell.revealed = true
    if (cell.adjacent > 0 || cell.mine) continue

    const row = Math.floor(index / MINES_SIZE)
    const column = index % MINES_SIZE
    for (let rowOffset = -1; rowOffset <= 1; rowOffset += 1) {
      for (let columnOffset = -1; columnOffset <= 1; columnOffset += 1) {
        if (rowOffset === 0 && columnOffset === 0) continue
        const nextRow = row + rowOffset
        const nextColumn = column + columnOffset
        if (
          nextRow < 0 ||
          nextRow >= MINES_SIZE ||
          nextColumn < 0 ||
          nextColumn >= MINES_SIZE
        ) {
          continue
        }
        queue.push(nextRow * MINES_SIZE + nextColumn)
      }
    }
  }

  return nextBoard
}

export function ArcadeDeck() {
  const [activeGame, setActiveGame] = useState<ArcadeGame>('snake')

  return (
    <div className="arcade-panel">
      <div className="arcade-toolbar">
        <button
          className={activeGame === 'snake' ? 'is-selected' : ''}
          onClick={() => setActiveGame('snake')}
        >
          Snake
        </button>
        <button
          className={activeGame === 'mines' ? 'is-selected' : ''}
          onClick={() => setActiveGame('mines')}
        >
          Mines
        </button>
      </div>

      {activeGame === 'snake' ? <SnakeGame /> : <MinesGame />}
    </div>
  )
}

function SnakeGame() {
  const [snake, setSnake] = useState<number[]>(() => buildInitialSnake())
  const [food, setFood] = useState<number>(() => createSnakeFood(buildInitialSnake()))
  const [score, setScore] = useState(0)
  const [bestScore, setBestScore] = useState(() => {
    const stored = window.localStorage.getItem(SNAKE_BEST_KEY)
    return stored ? Number(stored) || 0 : 0
  })
  const [isRunning, setIsRunning] = useState(false)
  const [status, setStatus] = useState('Warm up the grid and hit Start.')
  const directionRef = useRef<Direction>('right')

  const snakeSet = useMemo(() => new Set(snake), [snake])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === ' ') {
        event.preventDefault()
        setIsRunning((current) => !current)
        return
      }

      const nextDirection =
        event.key === 'ArrowUp' || event.key.toLowerCase() === 'w'
          ? 'up'
          : event.key === 'ArrowDown' || event.key.toLowerCase() === 's'
            ? 'down'
            : event.key === 'ArrowLeft' || event.key.toLowerCase() === 'a'
              ? 'left'
              : event.key === 'ArrowRight' || event.key.toLowerCase() === 'd'
                ? 'right'
                : null

      if (!nextDirection) return
      event.preventDefault()
      turnSnake(nextDirection)
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  useEffect(() => {
    if (!isRunning) return

    const timer = window.setInterval(() => {
      setSnake((currentSnake) => {
        const head = currentSnake[0]
        const nextHead = getNextSnakeHead(head, directionRef.current)

        if (
          hitsSnakeWall(head, nextHead, directionRef.current) ||
          currentSnake.includes(nextHead)
        ) {
          setIsRunning(false)
          setStatus('Signal lost. Restart to continue.')
          return currentSnake
        }

        const nextSnake =
          nextHead === food
            ? [nextHead, ...currentSnake]
            : [nextHead, ...currentSnake.slice(0, -1)]

        if (nextHead === food) {
          const nextScore = score + 1
          setScore(nextScore)
          if (nextScore > bestScore) {
            setBestScore(nextScore)
            window.localStorage.setItem(SNAKE_BEST_KEY, String(nextScore))
          }
          setFood(createSnakeFood(nextSnake))
          setStatus('Locked on. Keep the signal alive.')
        }

        return nextSnake
      })
    }, SNAKE_TICK_MS)

    return () => window.clearInterval(timer)
  }, [isRunning, food, score, bestScore])

  function resetSnake() {
    const initialSnake = buildInitialSnake()
    setSnake(initialSnake)
    setFood(createSnakeFood(initialSnake))
    setScore(0)
    setIsRunning(false)
    directionRef.current = 'right'
    setStatus('Grid reset. Press Start to play.')
  }

  function turnSnake(nextDirection: Direction) {
    if (isOppositeDirection(directionRef.current, nextDirection)) {
      return
    }
    directionRef.current = nextDirection
  }

  function toggleRunning() {
    setIsRunning((current) => {
      const nextRunning = !current
      setStatus(nextRunning ? 'Transmitting.' : 'Paused.')
      return nextRunning
    })
  }

  return (
    <div className="arcade-game">
      <div className="arcade-status">
        <span>Score: {score}</span>
        <span>Best: {bestScore}</span>
        <span>{status}</span>
      </div>

      <div
        className="snake-board"
        style={{ gridTemplateColumns: `repeat(${SNAKE_SIZE}, minmax(0, 1fr))` }}
      >
        {Array.from({ length: SNAKE_CELL_COUNT }, (_, index) => {
          const isHead = snake[0] === index
          const isBody = snakeSet.has(index)
          const isFood = food === index

          return (
            <div
              key={index}
              className={[
                'snake-cell',
                isHead ? 'is-head' : '',
                isBody ? 'is-body' : '',
                isFood ? 'is-food' : ''
              ]
                .filter(Boolean)
                .join(' ')}
            />
          )
        })}
      </div>

      <div className="arcade-actions">
        <button onClick={toggleRunning}>{isRunning ? 'Pause' : 'Start'}</button>
        <button onClick={resetSnake}>Reset</button>
      </div>

      <div className="snake-controls">
        <div />
        <button onClick={() => turnSnake('up')}>▲</button>
        <div />
        <button onClick={() => turnSnake('left')}>◀</button>
        <button onClick={() => turnSnake('down')}>▼</button>
        <button onClick={() => turnSnake('right')}>▶</button>
      </div>
    </div>
  )
}

function MinesGame() {
  const [board, setBoard] = useState<MineCell[]>(() => createMineBoard())
  const [flagMode, setFlagMode] = useState(false)
  const [status, setStatus] = useState('Reveal safe tiles or switch to flag mode.')
  const [gameComplete, setGameComplete] = useState(false)

  const flaggedCount = board.filter((cell) => cell.flagged).length
  const revealedSafeCount = board.filter((cell) => cell.revealed && !cell.mine).length
  const safeTileCount = board.length - MINES_COUNT

  function resetBoard() {
    setBoard(createMineBoard())
    setFlagMode(false)
    setGameComplete(false)
    setStatus('New board ready.')
  }

  function revealAllMines(nextBoard: MineCell[]) {
    return nextBoard.map((cell) => (cell.mine ? { ...cell, revealed: true } : cell))
  }

  function interactWithCell(index: number, forceFlag = false) {
    if (gameComplete) return

    setBoard((currentBoard) => {
      const currentCell = currentBoard[index]
      if (!currentCell || currentCell.revealed) {
        return currentBoard
      }

      if (forceFlag || flagMode) {
        const nextBoard = currentBoard.map((cell, cellIndex) =>
          cellIndex === index ? { ...cell, flagged: !cell.flagged } : cell
        )
        setStatus(nextBoard[index].flagged ? 'Flag planted.' : 'Flag cleared.')
        return nextBoard
      }

      if (currentCell.flagged) {
        return currentBoard
      }

      if (currentCell.mine) {
        setGameComplete(true)
        setStatus('Mine triggered. Reset the board.')
        return revealAllMines(currentBoard)
      }

      const nextBoard = revealSafeMineCells(currentBoard, index)
      const nextRevealedSafeCount = nextBoard.filter(
        (cell) => cell.revealed && !cell.mine
      ).length

      if (nextRevealedSafeCount >= safeTileCount) {
        setGameComplete(true)
        setStatus('Board cleared. Transmission stable.')
      } else {
        setStatus('Keep sweeping.')
      }

      return nextBoard
    })
  }

  return (
    <div className="arcade-game">
      <div className="arcade-status">
        <span>Mines: {MINES_COUNT}</span>
        <span>Flags: {flaggedCount}</span>
        <span>Safe: {revealedSafeCount}/{safeTileCount}</span>
      </div>

      <div className="arcade-actions">
        <button
          className={flagMode ? 'is-selected' : ''}
          onClick={() => setFlagMode((current) => !current)}
        >
          {flagMode ? 'Flag Mode: On' : 'Flag Mode: Off'}
        </button>
        <button onClick={resetBoard}>Reset</button>
      </div>

      <div className="mines-status">{status}</div>

      <div
        className="mines-board"
        style={{ gridTemplateColumns: `repeat(${MINES_SIZE}, minmax(0, 1fr))` }}
      >
        {board.map((cell, index) => (
          <button
            key={index}
            className={[
              'mine-cell',
              cell.revealed ? 'is-revealed' : '',
              cell.flagged ? 'is-flagged' : '',
              cell.mine && cell.revealed ? 'is-mine' : ''
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => interactWithCell(index)}
            onContextMenu={(event) => {
              event.preventDefault()
              interactWithCell(index, true)
            }}
          >
            {cell.revealed
              ? cell.mine
                ? '✹'
                : cell.adjacent > 0
                  ? cell.adjacent
                  : ''
              : cell.flagged
                ? '⚑'
                : ''}
          </button>
        ))}
      </div>
    </div>
  )
}
