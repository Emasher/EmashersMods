package emasher.sockets.modules;

import cpw.mods.fml.common.registry.GameRegistry;
import emasher.api.RSPulseModule;
import emasher.api.SideConfig;
import emasher.api.SocketTileAccess;
import emasher.sockets.SocketsMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

public class ModButton extends RSPulseModule {

	public ModButton( int id ) {
		super( id, "sockets:button0", "sockets:button1", "sockets:button2", "sockets:button3", "sockets:button4", "sockets:button5", "sockets:button6", "sockets:button7",
				"sockets:button8", "sockets:button9", "sockets:button10", "sockets:button11", "sockets:button12", "sockets:button13", "sockets:button14", "sockets:button15" );
	}

	@Override
	public String getLocalizedName() {
		return "Button";
	}
	
	@Override
	public void getToolTip( List l ) {
		l.add( "Creates an internal redstone pulse when pressed" );
		l.add( "Can be dyed different colours" );
	}
	
	@Override
	public void getIndicatorKey( List l ) {
		l.add( SocketsMod.PREF_RED + "RS circuits to pulse" );
		l.add( SocketsMod.PREF_DARK_PURPLE + "RS latches to toggle" );
	}
	
	@Override
	public void addRecipe() {
		GameRegistry.addShapedRecipe( new ItemStack( SocketsMod.module, 1, moduleID ), "t", "b", Character.valueOf( 't' ), Blocks.stone_button, Character.valueOf( 'r' ), Items.redstone,
				Character.valueOf( 'b' ), new ItemStack( SocketsMod.module, 1, 16 ) );
	}
	
	@Override
	public int getCurrentTexture( SideConfig config ) {
		return config.meta >> 3;
	}
	
	@Override
	public boolean isOutputingRedstone( SideConfig config, SocketTileAccess ts ) {
		return false;
	}
	
	@Override
	public boolean hasLatchIndicator() {
		return true;
	}
	
	@Override
	public void changeColour( int colour, SideConfig config, SocketTileAccess ts, ForgeDirection side ) {
		config.meta = colour;
		config.meta <<= 3;
		
		ts.sendClientSideState( side.ordinal() );
	}
	
	@Override
	public void onSideActivated( SocketTileAccess ts, SideConfig config, ForgeDirection side, EntityPlayer player ) {
		config.meta &= 0xfffffff8;
		config.meta++;
		
		for( int i = 0; i < 3; i++ ) {
			if( config.rsControl[i] ) {
				ts.modifyRS( i, true );
				
			}
			
			if( config.rsLatch[i] ) {
				boolean latch = ts.getRSLatch( i );
				ts.modifyLatch( i, !latch );
			}
		}
		
		ts.sendClientSideState( side.ordinal() );
	}
	
	@Override
	public void updateSide( SideConfig config, SocketTileAccess ts, ForgeDirection side ) {
		if( ( config.meta & 7 ) > 0 ) {
			config.meta++;
			
			if( ( config.meta & 7 ) >= 4 ) {
				config.meta &= 0xfffffff8;
				
				for( int i = 0; i < 3; i++ ) {
					if( config.rsControl[i] ) {
						ts.modifyRS( i, false );
					}
				}
				
				ts.sendClientSideState( side.ordinal() );
				
			}
			
			
		}
	}
	
	@Override
	public void onRSInterfaceChange( SideConfig config, int index, SocketTileAccess ts, ForgeDirection side, boolean on ) {
		if( !on ) {
			if( config.rsControl[index] && ( config.meta & 7 ) > 0 ) {
				ts.modifyRS( index, true );
			}
		}
	}

	@Override
	public boolean canModuleBeDyed() {
		return true;
	}

}
