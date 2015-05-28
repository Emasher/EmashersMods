package emasher.sockets.modules

import emasher.api.{SideConfig, SocketModule, SocketTileAccess, Util}
import emasher.sockets.SocketsMod
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.CraftingManager
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.oredict.ShapedOreRecipe

class ModHinge( id: Int ) extends SocketModule( id, "sockets:hinge" ) {

  override def getLocalizedName: String = "Hinge"

  override def addRecipe( ): Unit = {
    CraftingManager.getInstance( ).getRecipeList.asInstanceOf[ java.util.List[ Object ] ]
      .add( new ShapedOreRecipe( new ItemStack( SocketsMod.module, 1, moduleID ), "p", "m",
      Character.valueOf( 'p' ), Blocks.piston,
      Character.valueOf( 'm' ), new ItemStack( SocketsMod.blankSide ) ) )
  }

  override def getToolTip( l: java.util.List[ Object ] ): Unit = {
    l.add( "Moves an adjacent block so it is adjacent" )
    l.add( "to the other hinge installed on the socket" )
    l.add( "on a redstone pulse" )
    l.add( "Only two can be installed on a socket and" )
    l.add( "they must be on adjacent faces" )
  }

  override def getIndicatorKey( l: java.util.List[ Object ] ): Unit = {
    l.add( SocketsMod.PREF_RED + "Activate" )
  }

  override def hasRSIndicator: Boolean = true

  override def canBeInstalled( ts: SocketTileAccess, side: ForgeDirection ): Boolean = {
    var i = 0
    var times = 0
    var other = ForgeDirection.UNKNOWN
    for( i <- 0 to 5 ) {
      val d = ForgeDirection.getOrientation( i )
      val m = ts.getSide( d )
      if( m.isInstanceOf[ ModHinge ] ) {
        times += 1
        other = d
      }
      if( times >= 2 ) return false
    }

    if( side.getOpposite != other ) return true
    false
  }

  override def onRSInterfaceChange( config: SideConfig, index: Int, ts: SocketTileAccess, side: ForgeDirection, on: Boolean ): Unit = {
    if( !on ) return
    if( !config.rsControl( index ) ) return
    val otherHinge = getOtherHinge( side, ts )
    if( otherHinge == ForgeDirection.UNKNOWN ) return
    if( otherHinge.ordinal( ) < side.ordinal( ) ) return

    val x = ts.xCoord + side.offsetX
    val y = ts.yCoord + side.offsetY
    val z = ts.zCoord + side.offsetZ

    val nx = ts.xCoord + otherHinge.offsetX
    val ny = ts.yCoord + otherHinge.offsetY
    val nz = ts.zCoord + otherHinge.offsetZ

    if( !ts.getWorldObj( ).isAirBlock( x, y, z ) ) {
      val done = Util.moveBlock( ts.getWorldObj( ), x, y, z, nx, ny, nz )
      if( done ) {
        ts.getWorldObj( ).playSoundEffect( ts.xCoord + 0.5D, ts.yCoord + 0.5D, ts.zCoord + 0.5D, "tile.piston.out", 0.5F, ts.getWorldObj( ).rand.nextFloat( ) * 0.25F + 0.6F );
        ts.getWorldObj( ).playSoundEffect( ts.xCoord + 0.5D, ts.yCoord + 0.5D, ts.zCoord + 0.5D, "tile.piston.in", 0.5F, ts.getWorldObj( ).rand.nextFloat( ) * 0.25F + 0.6F );
      }

    } else if( !ts.getWorldObj( ).isAirBlock( nx, ny, nz ) ) {
      val done = Util.moveBlock( ts.getWorldObj( ), nx, ny, nz, x, y, z )
      if( done ) {
        ts.getWorldObj( ).playSoundEffect( ts.xCoord + 0.5D, ts.yCoord + 0.5D, ts.zCoord + 0.5D, "tile.piston.out", 0.5F, ts.getWorldObj( ).rand.nextFloat( ) * 0.25F + 0.6F );
        ts.getWorldObj( ).playSoundEffect( ts.xCoord + 0.5D, ts.yCoord + 0.5D, ts.zCoord + 0.5D, "tile.piston.in", 0.5F, ts.getWorldObj( ).rand.nextFloat( ) * 0.25F + 0.6F );
      }
    }
  }

  def getOtherHinge( side: ForgeDirection, ts: SocketTileAccess ): ForgeDirection = {
    var i = 0
    for( i <- 0 to 5 ) {
      val d = ForgeDirection.getOrientation( i )
      if( d != side && ts.getSide( d ).moduleID == moduleID ) return d
    }

    ForgeDirection.UNKNOWN
  }
}
