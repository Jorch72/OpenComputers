package li.cil.oc.common.component

import li.cil.oc.api.network.{Message, Node, Visibility}
import li.cil.oc.common.tileentity
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.{PackedColor, TextBuffer}
import li.cil.oc.{api, Settings}
import net.minecraft.nbt.NBTTagCompound
import scala.collection.convert.WrapAsScala._

class Buffer(val owner: Buffer.Owner) extends api.network.Environment {
  val node = api.Network.newNode(this, Visibility.Network).
    withComponent("screen").
    withConnector().
    create()

  val buffer = new TextBuffer(maxResolution, maxDepth)

  def maxResolution = Settings.screenResolutionsByTier(owner.tier)

  def maxDepth = Settings.screenDepthsByTier(owner.tier)

  // ----------------------------------------------------------------------- //

  def text = buffer.toString

  def lines = buffer.buffer

  def color = buffer.color

  // ----------------------------------------------------------------------- //

  def depth = buffer.depth

  def depth_=(value: PackedColor.Depth.Value) = {
    if (value > maxDepth)
      throw new IllegalArgumentException("unsupported depth")
    if (buffer.depth = value) {
      owner.onScreenDepthChange(value)
      true
    }
    else false
  }

  def foreground = buffer.foreground

  def foreground_=(value: Int) = {
    if (buffer.foreground != value) {
      val result = buffer.foreground
      buffer.foreground = value
      owner.onScreenColorChange(foreground, background)
      result
    }
    else value
  }

  def background = buffer.background

  def background_=(value: Int) = {
    if (buffer.background != value) {
      val result = buffer.background
      buffer.background = value
      owner.onScreenColorChange(foreground, background)
      result
    }
    else value
  }

  def resolution = buffer.size

  def resolution_=(value: (Int, Int)) = {
    val (w, h) = value
    val (mw, mh) = maxResolution
    if (w < 1 || h < 1 || w > mw || h > mw || h * w > mw * mh)
      throw new IllegalArgumentException("unsupported resolution")
    if (buffer.size = value) {
      if (node != null) {
        node.sendToReachable("computer.signal", "screen_resized", Int.box(w), Int.box(h))
      }
      owner.onScreenResolutionChange(w, h)
      true
    }
    else false
  }

  def get(col: Int, row: Int) = buffer.get(col, row)

  def set(col: Int, row: Int, s: String) = if (col < buffer.width && (col >= 0 || -col < s.length)) {
    // Make sure the string isn't longer than it needs to be, in particular to
    // avoid sending too much data to our clients.
    val (x, truncated) =
      if (col < 0) (0, s.substring(-col))
      else (col, s.substring(0, math.min(s.length, buffer.width - col)))
    if (buffer.set(x, row, truncated))
      owner.onScreenSet(x, row, truncated)
  }

  def fill(col: Int, row: Int, w: Int, h: Int, c: Char) =
    if (buffer.fill(col, row, w, h, c))
      owner.onScreenFill(col, row, w, h, c)

  def copy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) =
    if (buffer.copy(col, row, w, h, tx, ty))
      owner.onScreenCopy(col, row, w, h, tx, ty)

  // ----------------------------------------------------------------------- //

  override def onConnect(node: Node) {}

  override def onDisconnect(node: Node) {}

  override def onMessage(message: Message) {}

  // ----------------------------------------------------------------------- //

  // TODO remove compatibility check for older saves in version 1.3 or so.
  def load(nbt: NBTTagCompound) = {
    if (nbt.hasKey("node")) node.load(nbt.getCompoundTag("node"))
    else node.load(nbt.getCompoundTag(Settings.namespace + "node"))
    if (nbt.hasKey("buffer")) buffer.load(nbt.getCompoundTag("buffer"))
    else buffer.load(nbt.getCompoundTag(Settings.namespace + "buffer"))
  }

  // Null check for Waila (and other mods that may call this client side).
  def save(nbt: NBTTagCompound) = if (node != null) {
    // Happy thread synchronization hack! Here's the problem: GPUs allow direct
    // calls for modifying screens to give a more responsive experience. This
    // causes the following problem: when saving, if the screen is saved first,
    // then the executor runs in parallel and changes the screen *before* the
    // server thread begins saving that computer, the saved computer will think
    // it changed the screen, although the saved screen wasn't. To avoid that we
    // wait for all computers the screen is connected to to finish their current
    // execution and pausing them (which will make them resume in the next tick
    // when their update() runs).
    if (node.network != null) {
      for (node <- node.reachableNodes) node.host match {
        case host: tileentity.traits.Computer if !host.isPaused =>
          host.pause(0.1)
        case _ =>
      }
    }

    nbt.setNewCompoundTag(Settings.namespace + "node", node.save)
    nbt.setNewCompoundTag(Settings.namespace + "buffer", buffer.save)
  }
}

object Buffer {

  import li.cil.oc.client.gui

  trait Owner {
    protected var _currentGui: Option[gui.Buffer] = None

    def currentGui = _currentGui

    def currentGui_=(value: Option[gui.Buffer]) = _currentGui = value

    def tier: Int

    def onScreenColorChange(foreground: Int, background: Int)

    def onScreenCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int)

    def onScreenDepthChange(depth: PackedColor.Depth.Value)

    def onScreenFill(col: Int, row: Int, w: Int, h: Int, c: Char)

    def onScreenResolutionChange(w: Int, h: Int)

    def onScreenSet(col: Int, row: Int, s: String)
  }

}