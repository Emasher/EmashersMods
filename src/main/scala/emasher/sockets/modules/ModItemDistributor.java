package emasher.sockets.modules;

import cpw.mods.fml.common.registry.GameRegistry;
import emasher.api.SideConfig;
import emasher.api.SocketModule;
import emasher.api.SocketTileAccess;
import emasher.sockets.SocketsMod;
import net.minecraft.block.BlockHopper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

public class ModItemDistributor extends SocketModule {

	public ModItemDistributor( int id ) {
		super( id, "sockets:itemDistributor" );
	}

	@Override
	public String getLocalizedName() {
		return "Item Distributor";
	}
	
	@Override
	public void getToolTip( List l ) {
		l.add( "Accepts items from automation and evenly" );
		l.add( "distributes them between internal inventories" );
	}
	
	@Override
	public void getIndicatorKey( List l ) {
		l.add( SocketsMod.PREF_GREEN + "Inventory to exclude" );
		l.add( SocketsMod.PREF_RED + "RS control circuit" );
		l.add( SocketsMod.PREF_DARK_PURPLE + "RS control latch" );
	}
	
	@Override
	public void addRecipe() {
		GameRegistry.addShapedRecipe( new ItemStack( SocketsMod.module, 1, moduleID ), "t", "b", Character.valueOf( 't' ), Items.clock, Character.valueOf( 'r' ), Items.redstone,
				Character.valueOf( 'b' ), new ItemStack( SocketsMod.module, 1, 1 ) );
	}
	
	@Override
	public boolean hasInventoryIndicator() {
		return true;
	}
	
	@Override
	public boolean hasRSIndicator() {
		return true;
	}
	
	@Override
	public boolean hasLatchIndicator() {
		return true;
	}
	
	@Override
	public boolean isItemInterface() {
		return true;
	}
	
	@Override
	public boolean canInsertItems() {
		return true;
	}
	
	@Override
	public void init( SocketTileAccess ts, SideConfig config, ForgeDirection side ) {
		config.meta = 8;
	}
	
	@Override
	public int itemFill( ItemStack item, boolean doFill, SideConfig config, SocketTileAccess ts, ForgeDirection side ) {
		boolean canIntake = true;
		
		
		for( int i = 0; i < 3; i++ ) {
			if( config.rsControl[i] && ts.getRSControl( i ) ) canIntake = false;
			if( config.rsLatch[i] && ts.getRSLatch( i ) ) canIntake = false;
		}
		
		if( canIntake ) {
			int taken = 0;
			ItemStack balancedItem = item.copy();
			int returned = 0;
			
			do {
				while( config.tank == -1 || config.tank == 3 || config.tank == config.inventory )
					ts.nextTank( side.ordinal() );
				
				ItemStack bf = balancedItem.copy();
				bf.stackSize = 1;
				
				returned = ts.addItemInternal( bf, doFill, config.tank );
				balancedItem.stackSize -= returned;
				taken += returned;
				
				ts.nextTank( side.ordinal() );
				
			} while( returned != 0 && balancedItem.stackSize != 0 );
			
			return taken;
			
		}
		
		return 0;
	}
	
	@Override
	public void updateSide( SideConfig config, SocketTileAccess ts, ForgeDirection side ) {
		
		if( config.meta == 0 ) {
			config.meta = 16;
			
			int xo = ts.xCoord + side.offsetX;
			int yo = ts.yCoord + side.offsetY;
			int zo = ts.zCoord + side.offsetZ;
			
			TileEntity t = ts.getWorldObj().getTileEntity( xo, yo, zo );
			
			if( t != null && t instanceof TileEntityHopper ) {
				TileEntityHopper th = ( TileEntityHopper ) t;
				
				boolean canIntake = true;
				
				for( int i = 0; i < 3; i++ ) {
					if( config.rsControl[i] && ts.getRSControl( i ) ) canIntake = false;
					if( config.rsLatch[i] && ts.getRSLatch( i ) ) canIntake = false;
				}
				
				int direction = BlockHopper.getDirectionFromMetadata( ts.getWorldObj().getBlockMetadata( xo, yo, zo ) );
				if( ForgeDirection.getOrientation( direction ).getOpposite() == side && canIntake ) {
					for( int i = 0; i < th.getSizeInventory(); ++i ) {
						if( th.getStackInSlot( i ) != null ) {
							ItemStack itemstack = th.getStackInSlot( i ).copy();
							itemstack.stackSize = 1;
							int added = ts.addItem( itemstack, true, side );

							itemstack.stackSize = th.getStackInSlot( i ).stackSize - added;
							if( itemstack.stackSize <= 0 ) itemstack = null;

							th.setInventorySlotContents( i, itemstack );
						}
					}
				}
			}
		} else {
			config.meta--;
		}
		
	}

}
