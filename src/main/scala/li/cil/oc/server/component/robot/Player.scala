package li.cil.oc.server.component.robot

import cpw.mods.fml.common.ObfuscationReflectionHelper
import java.util.logging.Level
import li.cil.oc.common.tileentity
import li.cil.oc.util.mods.{Mods, UniversalElectricity, TinkersConstruct, PortalGun}
import li.cil.oc.{OpenComputers, Settings}
import net.minecraft.block.{BlockPistonBase, BlockFluid, Block}
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.{EnumStatus, EntityPlayer}
import net.minecraft.entity.{IMerchant, EntityLivingBase, Entity}
import net.minecraft.item.{Item, ItemBlock, ItemStack}
import net.minecraft.potion.PotionEffect
import net.minecraft.server.MinecraftServer
import net.minecraft.util._
import net.minecraft.world.World
import net.minecraftforge.common.{MinecraftForge, ForgeHooks, ForgeDirection}
import net.minecraftforge.event.entity.player.{EntityInteractEvent, PlayerInteractEvent}
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.event.{Event, ForgeEventFactory}
import net.minecraftforge.fluids.FluidRegistry
import scala.collection.convert.WrapAsScala._
import scala.reflect._

class Player(val robot: tileentity.Robot) extends EntityPlayer(robot.world, Settings.get.nameFormat.replace("$player$", robot.owner).replace("$random$", (robot.world.rand.nextInt(0xFFFFFF) + 1).toString)) {
  capabilities.allowFlying = true
  capabilities.disableDamage = true
  capabilities.isFlying = true
  onGround = true
  yOffset = 0.5f
  eyeHeight = 0f
  setSize(1, 1)

  val robotInventory = new Inventory(this)
  if (Mods.BattleGear2.isAvailable) {
    ObfuscationReflectionHelper.setPrivateValue(classOf[EntityPlayer], this, robotInventory, "inventory", "field_71071_by")
  }
  else inventory = robotInventory

  var facing, side = ForgeDirection.UNKNOWN

  var customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided: ItemStack = _

  def world = robot.world

  override def getPlayerCoordinates = new ChunkCoordinates(robot.x, robot.y, robot.z)

  // ----------------------------------------------------------------------- //

  def updatePositionAndRotation(facing: ForgeDirection, side: ForgeDirection) {
    this.facing = facing
    this.side = side
    // Slightly offset in robot's facing to avoid glitches (e.g. Portal Gun).
    val direction = Vec3.createVectorHelper(
      facing.offsetX + side.offsetX * 0.5 + robot.facing.offsetX * 0.01,
      facing.offsetY + side.offsetY * 0.5 + robot.facing.offsetY * 0.01,
      facing.offsetZ + side.offsetZ * 0.5 + robot.facing.offsetZ * 0.01).normalize()
    val yaw = Math.toDegrees(-Math.atan2(direction.xCoord, direction.zCoord)).toFloat
    val pitch = Math.toDegrees(-Math.atan2(direction.yCoord, Math.sqrt((direction.xCoord * direction.xCoord) + (direction.zCoord * direction.zCoord)))).toFloat * 0.99f
    setLocationAndAngles(robot.x + 0.5, robot.y, robot.z + 0.5, yaw, pitch)
    prevRotationPitch = rotationPitch
    prevRotationYaw = rotationYaw
  }

  def closestEntity[Type <: Entity : ClassTag](side: ForgeDirection = facing) = {
    val (x, y, z) = (robot.x + side.offsetX, robot.y + side.offsetY, robot.z + side.offsetZ)
    val bounds = AxisAlignedBB.getAABBPool.getAABB(x, y, z, x + 1, y + 1, z + 1)
    Option(world.findNearestEntityWithinAABB(classTag[Type].runtimeClass, bounds, this)).map(_.asInstanceOf[Type])
  }

  def entitiesOnSide[Type <: Entity : ClassTag](side: ForgeDirection) = {
    val (x, y, z) = (robot.x + side.offsetX, robot.y + side.offsetY, robot.z + side.offsetZ)
    entitiesInBlock[Type](x, y, z)
  }

  def entitiesInBlock[Type <: Entity : ClassTag](x: Int, y: Int, z: Int) = {
    val bounds = AxisAlignedBB.getAABBPool.getAABB(x, y, z, x + 1, y + 1, z + 1)
    world.getEntitiesWithinAABB(classTag[Type].runtimeClass, bounds).map(_.asInstanceOf[Type])
  }

  private def adjacentItems = {
    val bounds = AxisAlignedBB.getAABBPool.getAABB(robot.x - 2, robot.y - 2, robot.z - 2, robot.x + 3, robot.y + 3, robot.z + 3)
    world.getEntitiesWithinAABB(classOf[EntityItem], bounds).map(_.asInstanceOf[EntityItem])
  }

  private def collectDroppedItems(itemsBefore: Iterable[EntityItem]) {
    val itemsAfter = adjacentItems
    val itemsDropped = itemsAfter -- itemsBefore
    for (drop <- itemsDropped) {
      drop.delayBeforeCanPickup = 0
      drop.onCollideWithPlayer(this)
    }
  }

  // ----------------------------------------------------------------------- //

  override def attackTargetEntityWithCurrentItem(entity: Entity) {
    callUsingItemInSlot(0, stack => entity match {
      case player: EntityPlayer if !canAttackPlayer(player) => // Avoid player damage.
      case _ =>
        super.attackTargetEntityWithCurrentItem(entity)
        if (stack != null && entity.isDead) {
          robot.addXp(Settings.get.robotActionXp)
        }
    })
  }

  override def interactWith(entity: Entity) = {
    val cancel = try MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(this, entity)) catch {
      case t: Throwable =>
        if (!t.getStackTrace.exists(_.getClassName.startsWith("mods.battlegear2."))) {
          OpenComputers.log.log(Level.WARNING, "Some event handler screwed up!", t)
        }
        false
    }
    !cancel && callUsingItemInSlot(0, stack => {
      val current = getCurrentEquippedItem

      val result = isItemUseAllowed(stack) && (entity.interactFirst(this) || (entity match {
        case living: EntityLivingBase if current != null => current.func_111282_a(this, living)
        case _ => false
      }))
      if (current != null && current.stackSize <= 0) {
        destroyCurrentEquippedItem()
      }
      result
    })
  }

  def activateBlockOrUseItem(x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float, duration: Double): ActivationType.Value = {
    callUsingItemInSlot(0, stack => {
      if (shouldCancel(() => ForgeEventFactory.onPlayerInteract(this, Action.RIGHT_CLICK_BLOCK, x, y, z, side))) {
        return ActivationType.None
      }

      val item = if (stack != null) stack.getItem else null
      if (!PortalGun.isPortalGun(stack)) {
        if (item != null && item.onItemUseFirst(stack, this, world, x, y, z, side, hitX, hitY, hitZ)) {
          return ActivationType.ItemUsed
        }
      }

      val blockId = world.getBlockId(x, y, z)
      val block = Block.blocksList(blockId)
      val canActivate = block != null && Settings.get.allowActivateBlocks
      val shouldActivate = canActivate && (!isSneaking || (item == null || item.shouldPassSneakingClickToBlock(world, x, y, z)))
      val result =
        if (shouldActivate && block.onBlockActivated(world, x, y, z, this, side, hitX, hitY, hitZ))
          ActivationType.BlockActivated
        else if (isItemUseAllowed(stack) && tryPlaceBlockWhileHandlingFunnySpecialCases(stack, x, y, z, side, hitX, hitY, hitZ))
          ActivationType.ItemPlaced
        else if (tryUseItem(stack, duration))
          ActivationType.ItemUsed
        else
          ActivationType.None

      result
    })
  }

  def useEquippedItem(duration: Double) = {
    callUsingItemInSlot(0, stack => {
      if (!shouldCancel(() => ForgeEventFactory.onPlayerInteract(this, Action.RIGHT_CLICK_AIR, 0, 0, 0, ForgeDirection.UNKNOWN.ordinal))) {
        tryUseItem(stack, duration)
      }
      else false
    })
  }

  private def tryUseItem(stack: ItemStack, duration: Double) = {
    clearItemInUse()
    stack != null && stack.stackSize > 0 && isItemUseAllowed(stack) && {
      val oldSize = stack.stackSize
      val oldDamage = if (stack != null) stack.getItemDamage else 0
      val oldData = if (stack.hasTagCompound) stack.getTagCompound.copy() else null
      val heldTicks = math.max(0, math.min(stack.getMaxItemUseDuration, (duration * 20).toInt))
      // Change the offset at which items are used, to avoid hitting
      // the robot itself (e.g. with bows, potions, mining laser, ...).
      val offset = facing
      posX += offset.offsetX * 0.6
      posY += offset.offsetY * 0.6
      posZ += offset.offsetZ * 0.6
      val newStack = stack.useItemRightClick(world, this)
      if (isUsingItem) {
        val remaining = customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided.getMaxItemUseDuration - heldTicks
        customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided.onPlayerStoppedUsing(world, this, remaining)
        clearItemInUse()
      }
      posX -= offset.offsetX * 0.6
      posY -= offset.offsetY * 0.6
      posZ -= offset.offsetZ * 0.6
      robot.computer.pause(heldTicks / 20.0)
      // These are functions to avoid null pointers if newStack is null.
      def sizeOrDamageChanged = newStack.stackSize != oldSize || newStack.getItemDamage != oldDamage
      def tagChanged = (oldData == null && newStack.hasTagCompound) || (oldData != null && !newStack.hasTagCompound) ||
        (oldData != null && newStack.hasTagCompound && !oldData.equals(newStack.getTagCompound))
      val stackChanged = newStack != stack || (newStack != null && (sizeOrDamageChanged || tagChanged || PortalGun.isStandardPortalGun(stack)))
      if (stackChanged) {
        robot.setInventorySlotContents(0, newStack)
      }
      stackChanged
    }
  }

  def placeBlock(slot: Int, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    callUsingItemInSlot(slot, stack => {
      if (shouldCancel(() => ForgeEventFactory.onPlayerInteract(this, Action.RIGHT_CLICK_BLOCK, x, y, z, side))) {
        return false
      }

      tryPlaceBlockWhileHandlingFunnySpecialCases(stack, x, y, z, side, hitX, hitY, hitZ)
    }, repair = false)
  }

  def clickBlock(x: Int, y: Int, z: Int, side: Int): Double = {
    callUsingItemInSlot(0, stack => {
      if (shouldCancel(() => ForgeEventFactory.onPlayerInteract(this, Action.LEFT_CLICK_BLOCK, x, y, z, side))) {
        return 0
      }

      // TODO Is this already handled via the event?
      if (MinecraftServer.getServer.isBlockProtected(world, x, y, z, this)) {
        return 0
      }

      val blockId = world.getBlockId(x, y, z)
      val block = Block.blocksList(blockId)
      val metadata = world.getBlockMetadata(x, y, z)
      val mayClickBlock = blockId > 0 && block != null
      val canClickBlock = mayClickBlock &&
        !block.isAirBlock(world, x, y, z) &&
        FluidRegistry.lookupFluidForBlock(block) == null &&
        !block.isInstanceOf[BlockFluid]
      if (!canClickBlock) {
        return 0
      }

      val breakEvent = new BlockEvent.BreakEvent(x, y, z, world, block, metadata, this)
      MinecraftForge.EVENT_BUS.post(breakEvent)
      if (breakEvent.isCanceled) {
        return 0
      }

      block.onBlockClicked(world, x, y, z, this)
      world.extinguishFire(this, x, y, z, side)

      val isBlockUnbreakable = block.getBlockHardness(world, x, y, z) < 0
      val canDestroyBlock = !isBlockUnbreakable && block.canEntityDestroy(world, x, y, z, this)
      if (!canDestroyBlock) {
        return 0
      }

      if (world.getWorldInfo.getGameType.isAdventure && !isCurrentToolAdventureModeExempt(x, y, z)) {
        return 0
      }

      val cobwebOverride = block == Block.web && Settings.get.screwCobwebs

      if (!ForgeHooks.canHarvestBlock(block, this, metadata) && !cobwebOverride) {
        return 0
      }

      val hardness = block.getBlockHardness(world, x, y, z)
      val strength = getCurrentPlayerStrVsBlock(block, false, metadata)
      val breakTime =
        if (cobwebOverride) Settings.get.swingDelay
        else hardness * 1.5 / strength
      val adjustedBreakTime = math.max(0.05, breakTime * Settings.get.harvestRatio * math.max(1 - robot.level * Settings.get.harvestSpeedBoostPerLevel, 0))

      // Special handling for Tinkers Construct - tools like the hammers do
      // their break logic in onBlockStartBreak but return true to cancel
      // further processing. We also need to adjust our offset for their ray-
      // tracing implementation.
      if (TinkersConstruct.isInfiTool(stack)) {
        posY -= 1.62
        prevPosY = posY
      }
      val cancel = stack != null && stack.getItem.onBlockStartBreak(stack, x, y, z, this)
      if (cancel && TinkersConstruct.isInfiTool(stack)) {
        posY += 1.62
        prevPosY = posY
        return adjustedBreakTime
      }
      if (cancel) {
        return 0
      }

      world.playAuxSFXAtEntity(this, 2001, x, y, z, blockId + (metadata << 12))

      if (stack != null) {
        stack.onBlockDestroyed(world, blockId, x, y, z, this)
      }

      block.onBlockHarvested(world, x, y, z, metadata, this)
      if (block.removeBlockByPlayer(world, this, x, y, z)) {
        block.onBlockDestroyedByPlayer(world, x, y, z, metadata)
        // Note: the block has been destroyed by `removeBlockByPlayer`. This
        // check only serves to test whether the block can drop anything at all.
        if (block.canHarvestBlock(this, metadata)) {
          block.harvestBlock(world, this, x, y, z, metadata)
          robot.addXp(breakEvent.getExpToDrop * Settings.get.robotOreXpRate)
        }
        if (stack != null) {
          robot.addXp(Settings.get.robotActionXp)
        }
        return adjustedBreakTime
      }
      0
    })
  }

  private def isItemUseAllowed(stack: ItemStack) = stack == null || {
    (Settings.get.allowUseItemsWithDuration || stack.getMaxItemUseDuration <= 0) &&
      (!PortalGun.isPortalGun(stack) || PortalGun.isStandardPortalGun(stack)) &&
      !stack.isItemEqual(new ItemStack(Item.leash))
  }

  override def dropPlayerItemWithRandomChoice(stack: ItemStack, inPlace: Boolean) =
    robot.spawnStackInWorld(stack, if (inPlace) ForgeDirection.UNKNOWN else facing)

  private def shouldCancel(f: () => PlayerInteractEvent) = {
    try {
      val event = f()
      event.isCanceled || event.useBlock == Event.Result.DENY || event.useItem == Event.Result.DENY
    }
    catch {
      case t: Throwable =>
        if (!t.getStackTrace.exists(_.getClassName.startsWith("mods.battlegear2."))) {
          OpenComputers.log.log(Level.WARNING, "Some event handler screwed up!", t)
        }
        false
    }
  }

  private def callUsingItemInSlot[T](slot: Int, f: (ItemStack) => T, repair: Boolean = true) = {
    val itemsBefore = adjacentItems
    val stack = inventory.getStackInSlot(slot)
    val oldStack = if (stack != null) stack.copy() else null
    try {
      f(stack)
    }
    finally {
      val newStack = inventory.getStackInSlot(slot)
      if (newStack != null) {
        if (newStack.stackSize <= 0) {
          inventory.setInventorySlotContents(slot, null)
        }
        if (repair) {
          if (newStack.stackSize > 0) tryRepair(newStack, oldStack)
          else ForgeEventFactory.onPlayerDestroyItem(this, newStack)
        }
      }
      collectDroppedItems(itemsBefore)
    }
  }

  private def tryRepair(stack: ItemStack, oldStack: ItemStack) {
    def repair(high: Long, low: Long) = {
      val needsRepairing = low < high
      val damageRate = Settings.get.itemDamageRate * math.max(1 - robot.level * Settings.get.toolEfficiencyPerLevel, 0)
      val shouldRepair = needsRepairing && getRNG.nextDouble() >= damageRate
      if (shouldRepair) {
        // If an item takes a lot of damage at once we don't necessarily want to
        // make *all* of that damage go away. Instead we scale it according to
        // our damage probability. This makes sure we don't discard massive
        // damage spikes (e.g. on axes when using the TreeCapitator mod or such).
        math.ceil((high - low) * (1 - damageRate)).toInt
      }
      else 0
    }
    if (Mods.UniversalElectricity.isAvailable && UniversalElectricity.isEnergyItem(stack)) {
      UniversalElectricity.chargeItem(stack, repair(UniversalElectricity.getEnergyInItem(oldStack), UniversalElectricity.getEnergyInItem(stack)))
    }
    else if (stack.isItemStackDamageable) {
      stack.setItemDamage(stack.getItemDamage - repair(stack.getItemDamage, oldStack.getItemDamage))
    }
  }

  private def tryPlaceBlockWhileHandlingFunnySpecialCases(stack: ItemStack, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float) = {
    stack != null && stack.stackSize > 0 && {
      val fakeEyeHeight = if (rotationPitch < 0 && isSomeKindOfPiston(stack)) 1.82 else 0
      setPosition(posX, posY - fakeEyeHeight, posZ)
      val didPlace = stack.tryPlaceItemIntoWorld(this, world, x, y, z, side, hitX, hitY, hitZ)
      setPosition(posX, posY + fakeEyeHeight, posZ)
      if (didPlace) {
        robot.addXp(Settings.get.robotActionXp)
      }
      didPlace
    }
  }

  private def isSomeKindOfPiston(stack: ItemStack) =
    stack.getItem match {
      case itemBlock: ItemBlock if itemBlock.getBlockID > 0 =>
        val block = Block.blocksList(itemBlock.getBlockID)
        block != null && block.isInstanceOf[BlockPistonBase]
      case _ => false
    }

  // ----------------------------------------------------------------------- //

  override def setItemInUse(stack: ItemStack, useDuration: Int) {
    super.setItemInUse(stack, useDuration)
    customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided = stack
  }

  override def clearItemInUse() {
    super.clearItemInUse()
    customItemInUseBecauseMinecraftIsBloodyStupidAndMakesRandomMethodsClientSided = null
  }

  override def addExhaustion(amount: Float) {
    if (Settings.get.robotExhaustionCost > 0) {
      robot.bot.node.changeBuffer(-Settings.get.robotExhaustionCost * amount)
    }
    robot.addXp(Settings.get.robotExhaustionXpRate * amount)
  }

  override def openGui(mod: AnyRef, modGuiId: Int, world: World, x: Int, y: Int, z: Int) {}

  override def displayGUIMerchant(merchant: IMerchant, name: String) {
    merchant.setCustomer(null)
  }

  override def closeScreen() {}

  override def swingItem() {}

  override def canAttackPlayer(player: EntityPlayer) =
    Settings.get.canAttackPlayers && super.canAttackPlayer(player)

  override def canEat(value: Boolean) = false

  override def isPotionApplicable(effect: PotionEffect) = false

  override def attackEntityAsMob(entity: Entity) = false

  override def attackEntityFrom(source: DamageSource, damage: Float) = false

  override def isEntityInvulnerable = true

  override def heal(amount: Float) {}

  override def setHealth(value: Float) {}

  override def setDead() = isDead = true

  override def onDeath(source: DamageSource) {}

  override def onUpdate() {}

  override def onLivingUpdate() {}

  override def onItemPickup(entity: Entity, count: Int) {}

  override def setCurrentItemOrArmor(slot: Int, stack: ItemStack) {}

  override def setRevengeTarget(entity: EntityLivingBase) {}

  override def setLastAttacker(entity: Entity) {}

  override def mountEntity(entity: Entity) {}

  override def travelToDimension(dimension: Int) {}

  override def sleepInBedAt(x: Int, y: Int, z: Int) = EnumStatus.OTHER_PROBLEM

  override def canCommandSenderUseCommand(i: Int, s: String) = false

  override def sendChatToPlayer(message: ChatMessageComponent) {}
}
