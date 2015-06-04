package emasher.sockets;

import buildcraft.api.transport.IPipeTile;
import buildcraft.api.transport.IPipeTile.PipeType;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import emasher.api.*;
import emasher.sockets.modules.ModMachineOutput;
import emasher.sockets.packethandling.RequestInfoFromServerMessage;
import emasher.sockets.packethandling.SocketFluidMessage;
import emasher.sockets.packethandling.SocketItemMessage;
import emasher.sockets.packethandling.SocketStateMessage;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

public class TileSocket extends SocketTileAccess implements ISidedInventory, IFluidHandler, IEnergyHandler, IGasReceptor {
	public FluidTank[] tanks;
	public EnergyStorage capacitor;
	public InventoryBasic inventory;
	
	public boolean[] rsControl;
	public boolean[] rsLatch;
	
	public int[] sides;
	public SideConfig[] configs;
	public boolean[] sideRS;
	public boolean initialized = false;
	
	public int[] sideID;
	public int[] sideMeta;
	public boolean[] sideLocked;
	
	public int[] facID;
	public int[] facMeta;
	
	public boolean isRSShared;

	
	public TileSocket() {
		tanks = new FluidTank[3];
		rsControl = new boolean[3];
		rsLatch = new boolean[3];
		sideLocked = new boolean[6];
		facID = new int[6];
		facMeta = new int[6];
		isRSShared = false;
		
		for( int i = 0; i < 3; i++ ) {
			tanks[i] = new FluidTank( 8 * FluidContainerRegistry.BUCKET_VOLUME );
			rsControl[i] = false;
			rsLatch[i] = false;
		}
		
		inventory = new InventoryBasic( "socket", true, 3 );
		sideInventory = new InventoryBasic( "socketSide", true, 6 );
		
		capacitor = new EnergyStorage( 5000 );
		
		sides = new int[6];
		configs = new SideConfig[6];
		sideRS = new boolean[6];
		for( int i = 0; i < 6; i++ ) {
			sides[i] = 0;
			configs[i] = new SideConfig();
			sideRS[i] = false;
			sideLocked[i] = false;
			facID[i] = 0;
			facMeta[i] = 0;
		}
		
	}
	
	public void updateEntity() {
		super.updateEntity();
		
		
		if( !worldObj.isRemote ) {
			ForgeDirection d;
			SocketModule m;
			SideConfig c;
			for( int i = 0; i < 6; i++ ) {
				d = ForgeDirection.getOrientation( i );
				m = getSide( d );
				c = configs[i];

				m.updateSide( c, this, d );
			}
		}
		
		if( !initialized && worldObj != null ) {
			if( !worldObj.isRemote ) {
				
				sideID = new int[6];
				sideMeta = new int[6];
				
				for( int i = 0; i < 6; i++ ) {
					sideID[i] = -1;
					sideMeta[i] = -1;
					
					checkSideForChange( i );
				}
				
			}
			
			initialized = true;
		}
	}
	
	public void updateAllAdj() {
		ForgeDirection d;
		for( int i = 0; i < 6; i++ ) {
			d = ForgeDirection.getOrientation( i );
			updateAdj( d );
		}
	}
	
	public void updateAdj( ForgeDirection d ) {
		Block nblock = worldObj.getBlock( xCoord, yCoord, zCoord );
		int xo = xCoord + d.offsetX;
		int yo = yCoord + d.offsetY;
		int zo = zCoord + d.offsetZ;
		Block b = worldObj.getBlock( xo, yo, zo );
		if( b != null ) {
			b.onNeighborBlockChange( worldObj, xo, yo, zo, nblock );
		}
	}
	
	public void resetConfig( int side ) {
		configs[side] = new SideConfig();
		sideLocked[side] = false;
		sideInventory.setInventorySlotContents( side, null );
		SocketsMod.network.sendToDimension( new SocketStateMessage( this, ( byte ) side ), worldObj.provider.dimensionId );
	}
	
	@Override
	public void validate() {
		super.validate();
		if( this.worldObj.isRemote ) {
			for( int i = 0; i < 6; i++ ) {
				SocketsMod.network.sendToServer( new RequestInfoFromServerMessage( this, ( byte ) i, ( byte ) 0 ) );
			}
			for( int i = 0; i < 3; i++ ) {
				SocketsMod.network.sendToServer( new RequestInfoFromServerMessage( this, ( byte ) i, ( byte ) 1 ) );
				SocketsMod.network.sendToServer( new RequestInfoFromServerMessage( this, ( byte ) i, ( byte ) 2 ) );
			}
		} else {
			for( int i = 0; i < 6; i++ ) {
				sendClientSideState( i );
			}
			for( int i = 0; i < 3; i++ ) {
				sendClientInventorySlot( i );
				sendClientTankSlot( i );
			}
		}
	}
	
	@Override
	public void readFromNBT( NBTTagCompound data ) {
		super.readFromNBT( data );
		
		if( data.hasKey( "shareRS" ) ) {
			isRSShared = data.getBoolean( "shareRS" );
		}

		for( int i = 0; i < 3; i++ ) {
			if( data.hasKey( "tankCap" + i ) ) {
				tanks[i] = new FluidTank( data.getInteger( "tankCap" + i ) );
			}

			if( data.hasKey( "Fluid" + i ) ) {
				tanks[i].setFluid( FluidStack.loadFluidStackFromNBT( data.getCompoundTag( "Fluid" + i ) ) );
			}

			if( data.hasKey( "rsControl" + i ) ) rsControl[i] = data.getBoolean( "rsControl" + i );
			if( data.hasKey( "rsLatch" + i ) ) rsLatch[i] = data.getBoolean( "rsLatch" + i );
		}

		NBTTagList itemList = data.getTagList( "items", 10 );

		for( int i = 0; i < itemList.tagCount(); i++ ) {
			NBTTagCompound itemCompound = ( NBTTagCompound ) itemList.getCompoundTagAt( i );
			int slot = itemCompound.getInteger( "slot" );
			inventory.setInventorySlotContents( slot, ItemStack.loadItemStackFromNBT( itemCompound ) );
		}

		for( int i = 0; i < 6; i++ ) {
			if( data.hasKey( "side" + i ) ) {
				int loadedSide = data.getInteger( "side" + i );

				//Don't load item and fluid detectors
				if( loadedSide != 11 && loadedSide != 12) {
					sides[i] = loadedSide;
				} else {
					sides[i] = 0;
				}
			}
			if( data.hasKey( "config" + i ) ) {
				configs[i] = new SideConfig();
				configs[i].readFromNBT( data.getCompoundTag( "config" + i ) );
			}
			if( data.hasKey( "rs" + i ) ) {
				sideRS[i] = data.getBoolean( "rs" + i );
			}
			if( data.hasKey( "lock" + i ) ) {
				sideLocked[i] = data.getBoolean( "lock" + i );
			}
			if( data.hasKey( "facID" + i ) ) {
				facID[i] = data.getInteger( "facID" + i );
			}
			if( data.hasKey( "facMeta" + i ) ) {
				facMeta[i] = data.getInteger( "facMeta" + i );
			}
		}

		itemList = data.getTagList( "sideItems", 10 );

		for( int i = 0; i < itemList.tagCount(); i++ ) {
			NBTTagCompound itemCompound = ( NBTTagCompound ) itemList.getCompoundTagAt( i );
			int slot = itemCompound.getInteger( "slot" );
			sideInventory.setInventorySlotContents( slot, ItemStack.loadItemStackFromNBT( itemCompound ) );
		}

		capacitor.readFromNBT( data );
		int power = capacitor.getEnergyStored();
		if( data.hasKey( "powerCap2" ) ) this.setMaxEnergyStored( ( int ) data.getInteger( "powerCap2" ) );
		if( data.hasKey( "realPower" ) ) power = data.getInteger( "realPower" );
		capacitor.setEnergyStored( power );
	}
	

	@Override
	public void writeToNBT( NBTTagCompound data ) {
		super.writeToNBT( data );
		
		NBTTagList itemList = new NBTTagList();
		NBTTagList sideItemList = new NBTTagList();
		NBTTagCompound configData;
		
		data.setBoolean( "shareRS", isRSShared );
		
		for( int i = 0; i < 3; i++ ) {
			
			if( inventory.getStackInSlot( i ) != null ) {
				NBTTagCompound itemCompound = new NBTTagCompound();
				itemCompound.setInteger( "slot", i );
				inventory.getStackInSlot( i ).writeToNBT( itemCompound );
				itemList.appendTag( itemCompound );
			}
			
			if( tanks[i].getFluid() != null ) {
				data.setInteger( "tankCap" + i, tanks[i].getCapacity() );
				data.setTag( "Fluid" + i, tanks[i].getFluid().writeToNBT( new NBTTagCompound() ) );
			}
			
			data.setBoolean( "rsControl" + i, rsControl[i] );
			data.setBoolean( "rsLatch" + i, rsLatch[i] );
		}
		
		data.setTag( "items", itemList );
		
		
		for( int i = 0; i < 6; i++ ) {
			data.setInteger( "side" + i, sides[i] );
			configData = new NBTTagCompound();
			configs[i].writeToNBT( configData );
			data.setTag( "config" + i, configData );
			data.setBoolean( "rs" + i, sideRS[i] );
			data.setBoolean( "lock" + i, sideLocked[i] );
			data.setInteger( "facID" + i, facID[i] );
			data.setInteger( "facMeta" + i, facMeta[i] );
			
			if( sideInventory.getStackInSlot( i ) != null ) {
				NBTTagCompound itemCompound = new NBTTagCompound();
				itemCompound.setInteger( "slot", i );
				sideInventory.getStackInSlot( i ).writeToNBT( itemCompound );
				sideItemList.appendTag( itemCompound );
				
			}
			
		}
		
		data.setTag( "sideItems", sideItemList );
		
		if( capacitor != null ) {
			data.setInteger( "realPower", capacitor.getEnergyStored() );
			capacitor.writeToNBT( data );
			data.setInteger( "powerCap2", capacitor.getMaxEnergyStored() );
		}
		
	}
	
	@Override
	public SocketModule getSide( ForgeDirection direction ) {
		SocketModule result = ModuleRegistry.getModule( 0 );
		SocketModule temp = null;
		if( direction.ordinal() < 6 ) temp = ModuleRegistry.getModule( sides[direction.ordinal()] );
		if( temp != null ) result = temp;
		return result;
	}
	
	@Override
	public SideConfig getConfigForSide( ForgeDirection direction ) {
		return configs[direction.ordinal()];
	}
	
	public void lockSide( int side ) {
		sideLocked[side] = !sideLocked[side];
		SocketsMod.network.sendToDimension( new SocketStateMessage( this, ( byte ) side ), worldObj.provider.dimensionId );
	}
	
	public void checkSideForChange( int side ) {
		ForgeDirection d = ForgeDirection.getOrientation( side );
		SocketModule m = getSide( d );
		int xo = xCoord + d.offsetX;
		int yo = yCoord + d.offsetY;
		int zo = zCoord + d.offsetZ;
		boolean result = false;

		Block block = worldObj.getBlock( xo, yo, zo );
		int id = Block.getIdFromBlock( block );

		int meta = worldObj.getBlockMetadata( xo, yo, zo );
		if( ( id != sideID[side] && sideID[side] != 1 ) || ( meta != sideMeta[side] && sideMeta[side] != -1 ) ) {
			m.onAdjChangeSide( this, configs[side], d );
		}
		sideID[side] = id;
		sideMeta[side] = meta;
	}
	
	public int tankIndicatorIndex( int side ) {
		SideConfig c = configs[side];
		int temp;
		if( c.tank == -1 ) temp = 3;
		else temp = c.tank;
		return temp;
	}
	
	public int inventoryIndicatorIndex( int side ) {
		SideConfig c = configs[side];
		int temp;
		if( c.inventory == -1 ) temp = 3;
		else temp = c.inventory;
		return temp;
	}
	
	public int rsIndicatorIndex( int side ) {
		SideConfig c = configs[side];
		int temp = 0;
		
		if( c.rsControl[0] ) temp |= 1;
		if( c.rsControl[1] ) temp |= 2;
		if( c.rsControl[2] ) temp |= 4;
		
		return temp;
	}
	
	public int latchIndicatorIndex( int side ) {
		SideConfig c = configs[side];
		int temp = 0;
		
		if( c.rsLatch[0] ) temp |= 1;
		if( c.rsLatch[1] ) temp |= 2;
		if( c.rsLatch[2] ) temp |= 4;
		
		return temp;
	}
	
	public void nextTank( int side ) {
		configs[side].tank++;
		if( configs[side].tank == 3 ) configs[side].tank = -1;
		getSide( ForgeDirection.getOrientation( side ) ).indicatorUpdated( this, configs[side], ForgeDirection.getOrientation( side ) );
		SocketsMod.network.sendToDimension( new SocketStateMessage( this, ( byte ) side ), worldObj.provider.dimensionId );
	}
	
	public void nextInventory( int side ) {
		configs[side].inventory++;
		if( configs[side].inventory == 3 ) configs[side].inventory = -1;
		getSide( ForgeDirection.getOrientation( side ) ).indicatorUpdated( this, configs[side], ForgeDirection.getOrientation( side ) );
		SocketsMod.network.sendToDimension( new SocketStateMessage( this, ( byte ) side ), worldObj.provider.dimensionId );
	}
	
	public void nextRS( int side ) {
		boolean reset = false;
		SideConfig c = configs[side];
		
		if( c.rsControl[0] ) {
			if( c.rsControl[1] ) {
				c.rsControl[1] = false;
				if( c.rsControl[2] ) {
					reset = true;
					c.rsControl[0] = false;
					c.rsControl[2] = false;
				} else c.rsControl[2] = true;
			} else {
				c.rsControl[1] = true;
			}
		}
		
		if( !reset ) configs[side].rsControl[0] = !configs[side].rsControl[0];
		getSide( ForgeDirection.getOrientation( side ) ).indicatorUpdated( this, configs[side], ForgeDirection.getOrientation( side ) );
		SocketsMod.network.sendToDimension( new SocketStateMessage( this, ( byte ) side ), worldObj.provider.dimensionId );
	}
	
	public void nextLatch( int side ) {
		boolean reset = false;
		SideConfig c = configs[side];
		
		if( c.rsLatch[0] ) {
			if( c.rsLatch[1] ) {
				c.rsLatch[1] = false;
				if( c.rsLatch[2] ) {
					reset = true;
					c.rsLatch[0] = false;
					c.rsLatch[2] = false;
				} else c.rsLatch[2] = true;
			} else {
				c.rsLatch[1] = true;
			}
		}
		
		if( !reset ) configs[side].rsLatch[0] = !configs[side].rsLatch[0];
		getSide( ForgeDirection.getOrientation( side ) ).indicatorUpdated( this, configs[side], ForgeDirection.getOrientation( side ) );
		SocketsMod.network.sendToDimension( new SocketStateMessage( this, ( byte ) side ), worldObj.provider.dimensionId );
	}
	
	public void modifyRS( int cell, boolean on ) {
		if( rsControl[cell] != on ) {
			rsControl[cell] = on;

			for( int i = 0; i < 6; i++ ) {
				SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
				m.onRSInterfaceChange( configs[i], cell, this, ForgeDirection.getOrientation( i ), on );
				if( dead ) return;
			}
		}
	}
	
	@Override
	public void modifyLatch( int cell, boolean on ) {
		if( rsLatch[cell] != on ) {
			rsLatch[cell] = on;
			
			for( int i = 0; i < 6; i++ ) {
				SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
				m.onRSLatchChange( configs[i], cell, this, ForgeDirection.getOrientation( i ), on );
			}
		}
	}
	
	@Override
	public boolean getRSControl( int channel ) {
		return rsControl[channel];
	}

	@Override
	public boolean getRSLatch( int channel ) {
		return rsLatch[channel];
	}
	
	@Override
	public boolean getSideRS( ForgeDirection side ) {
		return this.sideRS[side.ordinal()];
	}
	

	// IInventory

	@Override
	public int getSizeInventory() {
		return 3;
	}

	@Override
	public ItemStack getStackInSlot( int slot ) {
		return inventory.getStackInSlot( slot );
	}

	@Override
	public ItemStack decrStackSize( int slot, int amnt ) {
		return this.extractItemInternal( true, slot, amnt );
	}

	@Override
	public ItemStack getStackInSlotOnClosing( int slot ) {
		return inventory.getStackInSlot( slot );
	}

	@Override
	public void setInventorySlotContents( int slot, ItemStack item ) {
		inventory.setInventorySlotContents( slot, item );
		for( int i = 0; i < 6; i++ ) {
			SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
			m.onInventoryChange( configs[i], slot, this, ForgeDirection.getOrientation( i ), true );
		}
	}

	@Override
	public void openInventory() {
	}

	@Override
	public void closeInventory() {

	}

	@Override
	public String getInventoryName() {
		return "socket";
	}

	@Override
	public boolean hasCustomInventoryName() {
		return false;
	}

	/*@Override
	public boolean isInvNameLocalized()
	{
		return false;
	}*/

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUseableByPlayer( EntityPlayer entityplayer ) {
		return true;
	}

	/*@Override
	public void openChest() {}

	@Override
	public void closeChest() {}*/

	@Override
	public boolean isItemValidForSlot( int i, ItemStack itemstack ) {
		return true;
	}

//	@Override
//	public void onInventoryChanged()
//	{
//		for(int i = 0; i < 6; i++)
//		{
//			for(int j = 0; j < 3; j++)
//			{
//				SocketModule m = getSide(ForgeDirection.getOrientation(i));
//				m.onInventoryChange(configs[i], j, this, ForgeDirection.getOrientation(i), false);
//			}
//		}
//	}
	
	// ISidedInventory
	
	@Override
	public int[] getAccessibleSlotsFromSide( int side ) {
		int[] result = new int[1];
		SocketModule m = getSide( ForgeDirection.getOrientation( side ) );
		
		if( m != null && ( m.canDirectlyExtractItems( configs[side], this ) || m.canDirectlyInsertItems( configs[side], this ) ) ) {
			SideConfig config = configs[side];
			if( config.inventory >= 0 && config.inventory <= 2 ) {
				result[0] = config.inventory;
			} else {
				result = new int[] {};
			}
		} else {
			result = new int[] {};
		}
		
		return result;
	}

	@Override
	public boolean canInsertItem( int slot, ItemStack is, int side ) {
		SocketModule m = getSide( ForgeDirection.getOrientation( side ) );
		if( m != null && m.canDirectlyInsertItems( configs[side], this ) ) {
			int[] slots = getAccessibleSlotsFromSide( side );
			if( slots.length > 0 ) {
				if( slots[0] == slot ) return true;
			}
		}
		
		return false;
	}

	@Override
	public boolean canExtractItem( int slot, ItemStack is, int side ) {
		SocketModule m = getSide( ForgeDirection.getOrientation( side ) );
		if( m != null && m.canDirectlyExtractItems( configs[side], this ) ) {
			int[] slots = getAccessibleSlotsFromSide( side );
			if( slots.length > 0 ) {
				if( slots[0] == slot ) return true;
			}
		}
		
		return false;
	}
	
	// ISpecialInventory
	
	@Override
	public ItemStack pullItem( ForgeDirection side, boolean doPull ) {
		int xo = xCoord + side.offsetX;
		int yo = yCoord + side.offsetY;
		int zo = zCoord + side.offsetZ;
		
		TileEntity t = worldObj.getTileEntity( xo, yo, zo );
		
		if( t instanceof IInventory ) {
//			if(t instanceof ISpecialInventory)
//			{
//				ISpecialInventory isi = (ISpecialInventory)t;
//				ItemStack[] items = isi.extractItem(doPull, side.getOpposite(), 1);
//				if(items != null && items.length > 0) return items[0];
//			}
//			else
			if( t instanceof ISidedInventory ) {
				ISidedInventory isi = ( ISidedInventory ) t;
				int[] slots = isi.getAccessibleSlotsFromSide( side.getOpposite().ordinal() );
				
				for( int i = 0; i < slots.length; i++ ) {
					ItemStack pulled = isi.getStackInSlot( slots[i] );
					if( pulled != null && isi.canExtractItem( slots[i], pulled, side.getOpposite().ordinal() ) ) {
						ItemStack result = null; //pulled.copy().splitStack(1);
						//pulled.stackSize--;
						//isi.setInventorySlotContents(slots[i], pulled);
						//if(pulled.stackSize <= 0) isi.setInventorySlotContents(slots[i], null);
						if( doPull ) {
							result = isi.decrStackSize( slots[i], 1 );

							//isi.onInventoryChanged();
						} else {
							result = pulled.copy().splitStack( 1 );
						}
						return result;
					}
				}
			} else {
				IInventory ii = ( IInventory ) t;
				
				for( int i = 0; i < ii.getSizeInventory(); i++ ) {
					ItemStack pulled = ii.getStackInSlot( i );
					if( pulled != null ) {
						ItemStack result = null;
						//pulled.stackSize--;
						//ii.setInventorySlotContents(i, pulled);
						//if(pulled.stackSize <= 0)ii.setInventorySlotContents(i, null);
						if( doPull ) {
							result = ii.decrStackSize( i, 1 );
							//TODO Check if it was needed
							//ii.onInventoryChanged();
						} else {
							result = pulled.copy().splitStack( 1 );
						}
						return result;
					}
				}
			}
		}
		
		return null;
	}
	
	public int addItemInternal( ItemStack stack, boolean doAdd, int inv ) {
		int amntAdded;
		int temp;
		
		if( inv > -1 && inv < 3 ) {
			ItemStack currStack = inventory.getStackInSlot( inv );
			
			if( currStack == null ) {
				if( doAdd ) {
					inventory.setInventorySlotContents( inv, stack.copy() );
					for( int i = 0; i < 6; i++ ) {
						SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
						m.onInventoryChange( configs[i], inv, this, ForgeDirection.getOrientation( i ), true );
					}
				}
				
				return stack.stackSize;
			} else if( currStack.isItemEqual( stack ) ) {
				temp = Math.min( currStack.stackSize + stack.stackSize, currStack.getItem().getItemStackLimit() );
				if( temp == ( currStack.stackSize + stack.stackSize ) ) {
					amntAdded = stack.stackSize;
				} else {
					amntAdded = currStack.getItem().getItemStackLimit() - currStack.stackSize;
				}
				
				if( doAdd && amntAdded > 0 ) {
					currStack.stackSize += amntAdded;
					for( int i = 0; i < 6; i++ ) {
						SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
						m.onInventoryChange( configs[i], inv, this, ForgeDirection.getOrientation( i ), true );
					}
				}
				
				return amntAdded;
			}
		}
		
		return 0;
	}

	public ItemStack extractItemInternal( boolean doRemove, int inv, int maxItemCount ) {
		ItemStack newStack;
		
		if( inv > -1 && inv < 3 ) {
			ItemStack currStack = inventory.getStackInSlot( inv );
			
			if( currStack != null ) {
				newStack = currStack.copy();
				newStack.stackSize = Math.min( currStack.stackSize, maxItemCount );
				if( doRemove ) {
					currStack.stackSize -= newStack.stackSize;
					if( currStack.stackSize <= 0 ) inventory.setInventorySlotContents( inv, null );
					for( int i = 0; i < 6; i++ ) {
						SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
						m.onInventoryChange( configs[i], inv, this, ForgeDirection.getOrientation( i ), false );
					}
				}
				
				return newStack;
			}
			
		}
		
		return null;
	}
	
	public ItemStack getStackInInventorySlot( int inv ) {
		return inventory.getStackInSlot( inv );
	}
	
	public void setInventoryStack( int inv, ItemStack stack ) {
		if( stack == null || stack.stackSize <= 0 ) inventory.setInventorySlotContents( inv, null );
		else inventory.setInventorySlotContents( inv, stack );
		
		for( int i = 0; i < 6; i++ ) {
			ForgeDirection d = ForgeDirection.getOrientation( i );
			getSide( d ).onInventoryChange( configs[i], inv, this, d, false );
		}
	}

	
	@Override
	public int addItem( ItemStack stack, boolean doAdd, ForgeDirection direction ) {
		if( direction.ordinal() >= 0 && direction.ordinal() < 6 ) {
			SocketModule m = getSide( direction );
			SideConfig c = configs[direction.ordinal()];
			if( m.isItemInterface() && m.canInsertItems() ) return m.itemFill( stack, doAdd, c, this, direction );
		}
		return 0;
	}

	//@Override
	public ItemStack[] extractItem( boolean doRemove, ForgeDirection direction, int maxItemCount ) {
		if( direction.ordinal() > 0 && direction.ordinal() < 6 ) {
			SocketModule m = getSide( direction );
			SideConfig c = configs[direction.ordinal()];
			
			if( m.isItemInterface() && m.canExtractItems() ) {
				ItemStack temp = m.itemExtract( maxItemCount, doRemove, c, this );
				if( temp != null ) return new ItemStack[] {temp};
			}
		}
		return new ItemStack[] {};
	}

	public boolean tryInsertItem( ItemStack stack, ForgeDirection side ) {
		int xo = xCoord + side.offsetX;
		int yo = yCoord + side.offsetY;
		int zo = zCoord + side.offsetZ;
		
		TileEntity t = worldObj.getTileEntity( xo, yo, zo );
		
		if( stack == null ) return false;
		
		if( t instanceof IInventory ) {
//			if(t instanceof ISpecialInventory)
//			{
//				ISpecialInventory isi = (ISpecialInventory)t;
//				ItemStack ghost = stack.copy().splitStack(1);
//				int used = isi.addItem(ghost, true, side.getOpposite());
//				if(used > 0) return true;
//
//			}
//			else
			if( t instanceof ISidedInventory ) {
				ISidedInventory isi = ( ISidedInventory ) t;
				ItemStack ghost = stack.copy().splitStack( 1 );
				int[] slots = isi.getAccessibleSlotsFromSide( side.getOpposite().ordinal() );
				
				for( int i = 0; i < slots.length; i++ ) {
					if( isi.canInsertItem( slots[i], ghost, side.getOpposite().ordinal() ) ) {
						ItemStack inSlot = isi.getStackInSlot( slots[i] );
						if( inSlot != null && inSlot.isItemEqual( ghost ) && inSlot.stackSize < inSlot.getMaxStackSize() && inSlot.stackSize < isi.getInventoryStackLimit() ) {
							inSlot.stackSize++;
							//TODO Check if it was needed
							// isi.onInventoryChanged();
							return true;
						}
					}
				}
				
				for( int i = 0; i < slots.length; i++ ) {
					if( isi.canInsertItem( slots[i], ghost, side.getOpposite().ordinal() ) ) {
						ItemStack inSlot = isi.getStackInSlot( slots[i] );
						if( inSlot == null ) {
							isi.setInventorySlotContents( slots[i], ghost );
							//TODO Check if it was needed
							// isi.onInventoryChanged();
							return true;
						}
					}
				}
				
				return false;
			} else {
				IInventory ii = ( IInventory ) t;
				ItemStack ghost = stack.copy().splitStack( 1 );
				
				for( int i = 0; i < ii.getSizeInventory(); i++ ) {
					if( ii.isItemValidForSlot( i, ghost ) ) {
						ItemStack inSlot = ii.getStackInSlot( i );
						if( inSlot != null && inSlot.isItemEqual( ghost ) && inSlot.stackSize < inSlot.getMaxStackSize() && inSlot.stackSize < ii.getInventoryStackLimit() ) {
							inSlot.stackSize++;
							//TODO Check if it was needed
							//ii.onInventoryChanged();
							return true;
						}
					}
				}
				
				for( int i = 0; i < ii.getSizeInventory(); i++ ) {
					if( ii.isItemValidForSlot( i, ghost ) ) {
						ItemStack inSlot = ii.getStackInSlot( i );
						if( inSlot == null ) {
							ii.setInventorySlotContents( i, ghost );
							//TODO Check if it was needed
							//ii.onInventoryChanged();
							return true;
						}
					}
				}
				
				return false;
			}
		}
		
		if( Loader.isModLoaded( "BuildCraft|Core" ) && t != null && stack != null ) {
			if( t instanceof IPipeTile ) {
				IPipeTile p = ( IPipeTile ) t;
				
				if( p.getPipeType() == PipeType.ITEM && p.isPipeConnected( side.getOpposite() ) ) {
					int res = p.injectItem( stack, false, side.getOpposite() );
					if( res == stack.stackSize ) {
						p.injectItem( stack, true, side.getOpposite() );
						stack.stackSize = 0;
						return true;
					}
				}
			}
		}
		
		return false;
	}

//	@Override
//	public int recieveGas(FluidStack gas, ForgeDirection direction)
//	{
//		SocketModule m = getSide(direction);
//
//		if(m.isGasInterface())
//		{
//			return m.gasFill(gas, this);
//		}
//
//		return 0;
//	}

	/*@Override
	public void setPowerHandler(IPowerHandler provider)
	{
		powerHandler = (PowerHandler) provider;
	}

	@Override
	public IPowerHandler getPowerHandler()
	{
		return powerHandler;
	}

	@Override
	public void doWork() {}

	@Override
	public int powerRequest(ForgeDirection direction)
	{
		SocketModule m = getSide(direction);
		
		if(m.isEnergyInterface(configs[direction.ordinal()]) && m.acceptsEnergy(configs[direction.ordinal()])) return Math.min(m.getPowerRequested(configs[direction.ordinal()], this), (int)(powerHandler.getMaxEnergyStored() - powerHandler.getEnergyStored()));
		else return 0;
	}*/


	@Override
	public int fill( ForgeDirection direction, FluidStack resource, boolean doFill ) {
		SocketModule m = getSide( direction );
		SideConfig c = configs[direction.ordinal()];
		
		if( m.isFluidInterface() && m.canInsertFluid() ) return m.fluidFill( resource, doFill, c, this, direction );
		return 0;
	}
	
	public int fillInternal( int tank, FluidStack resource, boolean doFill ) {
		if( tank >= 0 && tank < 3 ) {
			int result = tanks[tank].fill( resource, doFill );
			
			if( result > 0 && doFill ) {
				for( int i = 0; i < 6; i++ ) {
					SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
					m.onTankChange( configs[i], tank, this, ForgeDirection.getOrientation( i ), true );
				}
			}
			
			return result;
		}
		return 0;
	}
	
	/*@Override
	public FluidStack getFluidInTank(int tank)
	{
		return tanks[tank].getFluid();
	}

	@Override
	public int fill(int tankIndex, FluidStack resource, boolean doFill)
	{
		return 0;
	}*/

	@Override
	public FluidStack drain( ForgeDirection direction, int maxDrain, boolean doDrain ) {
		SocketModule m = getSide( direction );
		SideConfig c = configs[direction.ordinal()];
		
		if( m.isFluidInterface() && m.canExtractFluid() ) return m.fluidExtract( maxDrain, doDrain, c, this );
		return null;
	}
	
	public FluidStack drainInternal( int tank, int maxDrain, boolean doDrain ) {
		if( tank >= 0 && tank < 3 ) {
			FluidStack result = tanks[tank].drain( maxDrain, doDrain );
			
			if( result != null && doDrain ) {
				for( int i = 0; i < 6; i++ ) {
					SocketModule m = getSide( ForgeDirection.getOrientation( i ) );
					m.onTankChange( configs[i], tank, this, ForgeDirection.getOrientation( i ), false );
				}
			}
			
			return result;
		}
		return null;
	}

	/*@Override
	public FluidStack drain(int tankIndex, int maxDrain, boolean doDrain)
	{
		return null;
	}*/

	/*@Override
	public IFluidTank[] getTanks(ForgeDirection direction)
	{
		/*SocketModule m = getSide(direction);
		if(m != null)
		{
			SideConfig c = configs[direction.ordinal()];
			if(m.isFluidInterface()) return new IFluidTank[] {m.getAssociatedTank(c, this)};
		}
		return new IFluidTank[]{};
	}

	@Override
	public IFluidTank getTank(ForgeDirection direction, FluidStack type)
	{
		/*SocketModule m = getSide(direction);
		SideConfig c = configs[direction.ordinal()];
		
		if(m.isFluidInterface()) return m.getAssociatedTank(c, this);
		return null;
	}*/
	
	public void tryInsertFluid( int tank, ForgeDirection side ) {
		int xo = xCoord + side.offsetX;
		int yo = yCoord + side.offsetY;
		int zo = zCoord + side.offsetZ;
		
		TileEntity t = worldObj.getTileEntity( xo, yo, zo );
		
		if( tanks[tank].getFluid() != null && t != null && t instanceof IFluidHandler ) {
			IFluidHandler tn = ( IFluidHandler ) t;
			
			int amnt = tn.fill( side.getOpposite(), tanks[tank].getFluid(), true );
			this.drainInternal( tank, amnt, true );
		}
	}
	
	public void tryExtractFluid( int tank, ForgeDirection side, int volume ) {
		int xo = xCoord + side.offsetX;
		int yo = yCoord + side.offsetY;
		int zo = zCoord + side.offsetZ;
		
		TileEntity t = worldObj.getTileEntity( xo, yo, zo );
		
		if( t != null && t instanceof IFluidHandler ) {
			IFluidHandler tn = ( IFluidHandler ) t;
			
			FluidStack ghost = tn.drain( side.getOpposite(), volume, false );
			//int amnt = tanks[tank].fill(ghost, true);
			int amnt = this.fillInternal( tank, ghost, true );
			tn.drain( side.getOpposite(), amnt, true );
		}
	}

	/*@Override
	public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection direction)
	{
		SocketModule m = getSide(direction);
		return m.isEnergyInterface(configs[direction.ordinal()]) && m.acceptsEnergy(configs[direction.ordinal()]);
	}

	@Override
	public boolean isAddedToEnergyNet()
	{
		return addedToEnergyNet;
	}

	@Override
	public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection direction)
	{
		SocketModule m = getSide(direction);
		return m.isEnergyInterface(configs[direction.ordinal()]) && m.outputsEnergy(configs[direction.ordinal()]);
	}

	@Override
	public int getMaxEnergyOutput()
	{
		return 512;
	}

	/*@Override
	public int demandsEnergy(ForgeDirection direction)
	{
		return Math.min((int)(SocketsMod.EUPerMJ * 100), (int)powerHandler.getMaxEnergyStored() - (int)powerHandler.getEnergyStored());
	}

	@Override
	public int injectEnergy(ForgeDirection directionFrom, int amount)
	{
		SocketModule m = getSide(directionFrom);
		if(m.isEnergyInterface(configs[directionFrom.ordinal()]) && m.acceptsEnergy(configs[directionFrom.ordinal()]))
		{
			int euAmnt = (int)(amount * SocketsMod.EUPerMJ);
			int amnt = Math.min(amount, (int)powerHandler.getMaxEnergyStored() - (int)powerHandler.getEnergyStored());
			powerHandler.receiveEnergy(amnt, directionFrom);
			return amount - amnt;
		}
		
		return 0;
	}

	@Override
	public int getMaxSafeInput()
	{
		return 100000;
	}*/

	@Override
	public void sendClientSideState( int side ) {
		SocketsMod.network.sendToDimension( new SocketStateMessage( this, ( byte ) side ), worldObj.provider.dimensionId );
	}
	
	@Override
	public void sendClientInventorySlot( int inv ) {
		SocketsMod.network.sendToDimension( new SocketItemMessage( this, ( byte ) inv ), worldObj.provider.dimensionId );
	}
	
	@Override
	public void sendClientTankSlot( int tank ) {
		SocketsMod.network.sendToDimension( new SocketFluidMessage( this, ( byte ) tank ), worldObj.provider.dimensionId );
	}

	@Override
	public void outputEnergy( int amount, ForgeDirection side ) {
		int xo = xCoord + side.offsetX;
		int yo = yCoord + side.offsetY;
		int zo = zCoord + side.offsetZ;
		
		TileEntity t = worldObj.getTileEntity( xo, yo, zo );
		
		if( t != null && t instanceof IEnergyHandler ) {
			IEnergyHandler ieh = ( IEnergyHandler ) t;
			int amnt = ieh.receiveEnergy( side.getOpposite(), capacitor.extractEnergy( 1000, true ), false );
			capacitor.extractEnergy( amnt, false );
		}
	}
	
	@Override
	public int getMaxEnergyStored() {
		return capacitor.getMaxEnergyStored();
	}
	
	@Override
	public void setMaxEnergyStored( int newMax ) {
		capacitor.setCapacity( newMax );
	}

	@Override
	public int getEnergyStored() {
		return capacitor.getEnergyStored();
	}
	
	@Override
	public int useEnergy( int toUse, boolean simulate ) {
		return capacitor.extractEnergy( toUse, simulate );
	}

	@Override
	public int addEnergy( int energy, boolean simulate ) {
		return capacitor.receiveEnergy( energy, simulate );
		
	}

	@Override
	public FluidStack drain( ForgeDirection from, FluidStack resource, boolean doDrain ) {
		if( resource == null || !resource.isFluidEqual( drain( from, resource.amount, false ) ) ) {
			return null;
		}
		return drain( from, resource.amount, doDrain );
	}

	@Override
	public boolean canFill( ForgeDirection from, Fluid fluid ) {
		return this.getSide( from ).canInsertFluid();
	}

	@Override
	public boolean canDrain( ForgeDirection from, Fluid fluid ) {
		return this.getSide( from ).canExtractFluid();
	}

	@Override
	public FluidTankInfo[] getTankInfo( ForgeDirection from ) {
		return new FluidTankInfo[] {tanks[0].getInfo(), tanks[1].getInfo(), tanks[2].getInfo()};
	}

	@Override
	public FluidStack getFluidInTank( int tank ) {
		if( tank < 0 || tank >= 3 ) return null;
		return tanks[tank].getFluid();
	}

	@Override
	public int forceOutputItem( ItemStack stack, boolean doOutput ) {
		int origAmnt = stack.stackSize;
		
		for( int i = 0; i < 6; i++ ) {
			ForgeDirection d = ForgeDirection.getOrientation( i );
			if( getSide( d ) instanceof ModMachineOutput ) {
				SideConfig mConfig = configs[i];
				if( mConfig.inventory >= 0 && mConfig.inventory < 3 ) {
					int amnt = this.addItemInternal( stack, doOutput, mConfig.inventory );
					if( doOutput ) stack.stackSize -= amnt;
					getSide( d ).updateSide( configs[i], this, d );
					return amnt;
				}
			}
		}
		
		return 0;
	}


	@Override
	public int forceOutputFluid( FluidStack stack, boolean doOutput ) {
		int origAmnt = stack.amount;
		for( int i = 0; i < 6; i++ ) {
			ForgeDirection d = ForgeDirection.getOrientation( i );
			if( getSide( d ) instanceof ModMachineOutput ) {
				SideConfig mConfig = configs[i];
				if( mConfig.tank >= 0 && mConfig.tank < 3 ) {
					int amnt = this.fillInternal( mConfig.tank, stack, doOutput );
					if( doOutput ) stack.amount -= amnt;
					getSide( d ).updateSide( configs[i], this, d );
					return amnt;
				}
			}
		}
		
		return 0;
		
	}

	@Override
	@SideOnly( Side.CLIENT )
	public IIcon getTexture( int texture, int moduleID ) {
		return ( ( BlockSocket ) SocketsMod.socket ).textures[moduleID][texture];
	}
	
	// IGasReceptor
	
	@Override
	public int recieveGas( FluidStack gas, ForgeDirection direction, boolean doFill ) {
		return this.fill( direction, gas, doFill );
	}
	
	// IEnergyHandler

	@Override
	public int receiveEnergy( ForgeDirection from, int maxReceive, boolean simulate ) {
		if( from == ForgeDirection.UNKNOWN ) return 0;
		SocketModule m = getSide( from );
		return m.receiveEnergy( maxReceive, simulate, configs[from.ordinal()], this );
	}

	@Override
	public int extractEnergy( ForgeDirection from, int maxExtract, boolean simulate ) {
		if( from == ForgeDirection.UNKNOWN ) return 0;
		SocketModule m = getSide( from );
		return m.extractEnergy( maxExtract, simulate, configs[from.ordinal()], this );
	}

	@Override
	public boolean canConnectEnergy( ForgeDirection from ) {
		if( from == ForgeDirection.UNKNOWN ) return false;
		SocketModule m = getSide( from );
		return m.isEnergyInterface( configs[from.ordinal()] );
	}

	@Override
	public int getEnergyStored( ForgeDirection from ) {
		return capacitor.getEnergyStored();
	}

	@Override
	public int getMaxEnergyStored( ForgeDirection from ) {
		return capacitor.getMaxEnergyStored();
	}
}
