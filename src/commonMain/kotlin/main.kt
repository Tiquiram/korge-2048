import com.soywiz.klock.*
import com.soywiz.korev.*
import com.soywiz.korge.*
import com.soywiz.korge.animate.*
import com.soywiz.korge.html.Html
import com.soywiz.korge.input.*
import com.soywiz.korge.service.storage.*
import com.soywiz.korge.tween.*
import com.soywiz.korge.ui.*
import com.soywiz.korge.view.*
import com.soywiz.korim.color.*
import com.soywiz.korim.font.*
import com.soywiz.korim.format.*
import com.soywiz.korim.text.TextAlignment
import com.soywiz.korio.async.*
import com.soywiz.korio.async.ObservableProperty
import com.soywiz.korio.file.std.*
import com.soywiz.korma.geom.*
import com.soywiz.korma.geom.vector.*
import com.soywiz.korma.interpolation.*
import kotlin.collections.set
import kotlin.properties.*
import kotlin.random.*

fun create_bgField(parent: Container, fieldSize: Double, leftIndent: Double, topIndent: Double): RoundRect {
	return parent.roundRect(fieldSize, fieldSize, 5.0, fill = Colors["#b9aea0"]) {
		position(leftIndent, topIndent)
	}
}
var cellSize: Double = 0.0
var fieldSize: Double = 0.0
var leftIndent: Double = 0.0
var topIndent: Double = 0.0
var font: BitmapFont by Delegates.notNull()
var map = PositionMap()
val blocks = mutableMapOf<Int, Block>()

var freeId = 0
fun columnX(number: Int) = leftIndent + 10 + (cellSize + 10) * number
fun rowY(number: Int) = topIndent + 10 + (cellSize + 10) * number
fun Container.createNewBlockWithId(id: Int, number: Number, position: Position) {
	blocks[id] = block(number).position(columnX(position.x), rowY(position.y))
}
fun Container.createNewBlock(number: Number, position: Position): Int {
	val id = freeId++
	createNewBlockWithId(id, number, position)
	return id
}

fun Container.generateBlock() {
	val position = map.getRandomFreePosition() ?: return
	val number = if (Random.nextDouble() < 0.9) Number.ZERO else Number.ONE
	val newId = createNewBlock(number, position)
	map[position.x, position.y] = newId
}

fun Container.showGameOver(onRestart: () -> Unit) = container {
	val format = TextFormat(
		color = RGBA(0, 0, 0),
		size = 40,
		font = Html.FontFace.Bitmap(font)
	)
	val skin = TextSkin(
		normal = format,
		over = format.copy(color = RGBA(90, 90, 90)),
		down = format.copy(color = RGBA(120, 120, 120))
	)

	fun restart() {
		this@container.removeFromParent()
		onRestart()
	}

	position(leftIndent, topIndent)

	roundRect(fieldSize, fieldSize, 5.0, color = Colors["#FFFFFF33"])
	text("Game Over", 60.0, Colors.BLACK, font) {
		centerBetween(0.0, 0.0, fieldSize, fieldSize)
		y -= 60
	}
	uiText("Try again", 120.0, 35.0, skin) {
		centerBetween(0.0, 0.0, fieldSize, fieldSize)
		y += 20
		onClick { restart() }
	}

	onKeyDown {
		when (it.key) {
			Key.ENTER, Key.SPACE -> restart()
			else -> Unit
		}
	}
}



fun Stage.moveBlocksTo(direction: Direction) {
	if (isAnimationRunning) return
	if (!map.hasAvailableMoves()) {
		if (!isGameOver) {
			isGameOver = true
			showGameOver {
				isGameOver = false
				restart()
			}
		}
		return
	}
}

var isAnimationRunning = false
var isGameOver = false

suspend fun main() = Korge(width = 480, height = 640, title = "2048", bgcolor = RGBA(253, 247, 240)) {
	font = resourcesVfs["clear_sans.fnt"].readBitmapFont()

	cellSize = views.virtualWidth / 5.0
	fieldSize = 50 + 4 * cellSize
	leftIndent = (views.virtualWidth - fieldSize) / 2
	topIndent = 150.0

	val bgField = create_bgField(this, fieldSize, leftIndent, topIndent)

	//Adding Cells to the field
	this.graphics {
		position(leftIndent, topIndent)
		fill(Colors["#cec0b2"]) {
			for (i in 0..3) {
				for (j in 0..3) {
					roundRect(10 + (10 + cellSize) * i, 10 + (10 + cellSize) * j, cellSize, cellSize, 5.0)
				}
			}
		}
	}

	val bgLogo = roundRect(cellSize, cellSize, 5.0, fill = Colors["#edc403"]) {
		position(leftIndent, 30.0)
	}
	//Relative Positioning
	val bgBest = roundRect(cellSize * 1.5, cellSize * 0.8, 5.0, fill = Colors["#bbae9e"]) {
		alignRightToRightOf(bgField)
		alignTopToTopOf(bgLogo)
	}
	val bgScore = roundRect(cellSize * 1.5, cellSize * 0.8, 5.0, fill = Colors["#bbae9e"]) {
		alignRightToLeftOf(bgBest, 24)
		alignTopToTopOf(bgBest)
	}
	// TODO: Center our Numbers
	val font = resourcesVfs["clear_sans.fnt"].readBitmapFont()
	text("2048", cellSize * 0.5, Colors.WHITE, font).centerOn(bgLogo)

	text("BEST", cellSize * 0.25, RGBA(239, 226, 210), font) {
		centerXOn(bgBest)
		alignTopToTopOf(bgBest, 5.0)
	}

	text("0", cellSize * 0.5, Colors.WHITE, font) {
		setTextBounds(Rectangle(0.0, 0.0, bgBest.width, cellSize - 24.0))
		alignment = TextAlignment.MIDDLE_CENTER
		alignTopToTopOf(bgBest, 12.0)
		centerXOn(bgBest)
	}
	text("SCORE", cellSize * 0.25, RGBA(239, 226, 210), font) {
		centerXOn(bgScore)
		alignTopToTopOf(bgScore, 5.0)
	}
	text("0", cellSize * 0.5, Colors.WHITE, font) {
		setTextBounds(Rectangle(0.0, 0.0, bgScore.width, cellSize - 24.0))
		alignment = TextAlignment.MIDDLE_CENTER
		centerXOn(bgScore)
		alignTopToTopOf(bgScore, 12.0)
	}

	val restartImg = resourcesVfs["restart.png"].readBitmap()
	val undoImg = resourcesVfs["undo.png"].readBitmap()

	val btnSize = cellSize * 0.3
	val restartBlock = container {
		val background = roundRect(btnSize, btnSize, 5.0, fill = RGBA(185, 174, 160))
		image(restartImg) {
			size(btnSize * 0.8, btnSize * 0.8)
			centerOn(background)
		}
		alignTopToBottomOf(bgBest, 5)
		alignRightToRightOf(bgField)
	}
	val undoBlock = container {
		val background = roundRect(btnSize, btnSize, 5.0, fill = RGBA(185, 174, 160))
		image(undoImg) {
			size(btnSize * 0.6, btnSize * 0.6)
			centerOn(background)
		}
		alignTopToTopOf(restartBlock)
		alignRightToLeftOf(restartBlock, 5.0)
	}
	generateBlock()

	keys.down {
		when (it.key) {
			Key.LEFT -> moveBlocksTo(Direction.LEFT)
			Key.RIGHT -> moveBlocksTo(Direction.RIGHT)
			Key.UP -> moveBlocksTo(Direction.TOP)
			Key.DOWN -> moveBlocksTo(Direction.BOTTOM)
			else -> Unit
		}
	}

	onSwipe(20.0) {
		when (it.direction) {
			SwipeDirection.LEFT -> moveBlocksTo(Direction.LEFT)
			SwipeDirection.RIGHT -> moveBlocksTo(Direction.RIGHT)
			SwipeDirection.TOP -> moveBlocksTo(Direction.TOP)
			SwipeDirection.BOTTOM -> moveBlocksTo(Direction.BOTTOM)
		}
	}
}