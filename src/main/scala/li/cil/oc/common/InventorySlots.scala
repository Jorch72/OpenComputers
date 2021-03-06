package li.cil.oc.common

import li.cil.oc.api.driver.Slot

object InventorySlots {
  val computer = Array(
    Array(
      InventorySlot(Slot.Card, Tier.One),
      InventorySlot(Slot.Card, Tier.One),
      InventorySlot(Slot.Memory, Tier.One),
      InventorySlot(Slot.HardDiskDrive, Tier.One),
      InventorySlot(Slot.Processor, Tier.One),
      InventorySlot(Slot.Memory, Tier.One)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.One),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.HardDiskDrive, Tier.Two),
      InventorySlot(Slot.HardDiskDrive, Tier.One),
      InventorySlot(Slot.Processor, Tier.Two)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Two),
      InventorySlot(Slot.Disk, Tier.One),
      InventorySlot(Slot.Processor, Tier.Three)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.Disk, Tier.One),
      InventorySlot(Slot.Processor, Tier.Three)
    )
  )

  val server = Array(
    Array(
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Processor, Tier.Two),
      InventorySlot(Slot.Processor, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.HardDiskDrive, Tier.Two),
      InventorySlot(Slot.HardDiskDrive, Tier.Two)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Processor, Tier.Three),
      InventorySlot(Slot.Processor, Tier.Three),
      InventorySlot(Slot.Processor, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Processor, Tier.Three),
      InventorySlot(Slot.Processor, Tier.Three),
      InventorySlot(Slot.Processor, Tier.Three),
      InventorySlot(Slot.Processor, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.HardDiskDrive, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.Two)
    )
  )

  object Tier {
    val One = 0
    val Two = 1
    val Three = 2
  }

  case class InventorySlot(slot: Slot, tier: Int)
}
