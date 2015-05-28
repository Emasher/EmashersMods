package emasher.sockets.modules;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import emasher.api.SideConfig;
import emasher.api.SocketModule;
import emasher.api.SocketTileAccess;
import emasher.sockets.BlockSocket;
import emasher.sockets.SocketsMod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

public class ModInternalClock extends SocketModule {
	public static final int[] settings = new int[] {20, 40, 80, 160, 200, 600, 1200, 2400};

	public ModInternalClock( int id ) {
		super( id, "sockets:clock" );
	}

	@Override
	public String getLocalizedName() {
		return "Internal Clock";
	}

	@Override
	public void getToolTip( List l ) {
		l.add( "Creates an internal redstone pulse periodically" );
	}

	@Override
	public void getIndicatorKey( List l ) {
		l.add( SocketsMod.PREF_RED + "Output Channel" );
		l.add( SocketsMod.PREF_WHITE + "Modify length of delay" );
	}

	@Override
	public void addRecipe() {
		GameRegistry.addShapedRecipe( new ItemStack( SocketsMod.module, 1, moduleID ), "b",
				Character.valueOf( 'b' ), new ItemStack( SocketsMod.module, 1, 35 ) );
	}

	@Override
	public boolean hasRSIndicator() {
		return true;
	}

	@Override
	public boolean hasLatchIndicator() {
		return true;
	}

	@SideOnly( Side.CLIENT )
	public String getInternalTexture( SocketTileAccess ts, SideConfig config, ForgeDirection side ) {
		int timer = config.meta & 7;
		if( timer == 0 ) return "sockets:inner_latch_inactive";
		return "sockets:inner_latch_active";
	}

	@SideOnly( Side.CLIENT )
	public String[] getAllInternalTextures() {
		return new String[] {
				"sockets:inner_latch_inactive",
				"sockets:inner_latch_active"
		};
	}

	@SideOnly( Side.CLIENT )
	public IIcon[] getAdditionalOverlays( SocketTileAccess ts, SideConfig config, ForgeDirection side ) {
		int setting = 0;
		int time = 0;
		int on = 0;

		setting = config.meta >> 3;
		setting &= 7;
		on = config.meta & 7;

		time = config.meta >> 6;

		time = ( int ) Math.ceil( ( time * 7 ) / settings[setting] );

		if( on > 0 ) time = 7;

		return new IIcon[] {( ( BlockSocket ) SocketsMod.socket ).bar1[setting], ( ( BlockSocket ) SocketsMod.socket ).bar2[time]};
	}

	@Override
	@SideOnly( Side.CLIENT )
	public boolean flipBottomOverlay() {
		return true;
	}

	@Override
	public void updateSide( SideConfig config, SocketTileAccess ts, ForgeDirection side ) {
		int setting = 0;
		int oldTime = 0;
		int reBuild = 0;
		int on = config.meta & 7;
		boolean reRender = false;
		boolean updateAdj = false;
		boolean allOff = true;

		setting = config.meta >> 3;
		setting &= 7;

		oldTime = config.meta >> 6;
		int time = oldTime;

		boolean doInc = false;

		for( int i = 0; i < 3; i++ ) {
			if( config.rsLatch[i] ) {
				allOff = false;
				if( ts.getRSLatch( i ) ) doInc = true;
			}

			if( allOff ) doInc = true;
		}

		if( doInc ) {
			time++;
			oldTime = ( int ) Math.ceil( ( oldTime * 7 ) / settings[setting] );
			int timeDisp = ( int ) Math.ceil( ( time * 7 ) / settings[setting] );
			if( oldTime != timeDisp ) reRender = true;

			if( time >= settings[setting] ) {
				time = 0;
				on = 1;
				reRender = true;
				updateAdj = true;
				for( int i = 0; i < 3; i++ ) {
					if( config.rsControl[i] ) {
						ts.modifyRS( i, true );
					}
				}
			}
		}

		if( on != 0 ) on++;
		if( on >= 5 ) {
			on = 0;
			reRender = true;
			updateAdj = true;
			for( int i = 0; i < 3; i++ ) {
				if( config.rsControl[i] ) {
					ts.modifyRS( i, false );
				}
			}
		}

		reBuild = time;
		reBuild <<= 3;
		reBuild |= setting;
		reBuild <<= 3;
		reBuild |= on;

		config.meta = reBuild;

		if( reRender ) {
			ts.sendClientSideState( side.ordinal() );

		}

		if( updateAdj ) {
			ts.updateAdj( side );
		}
	}


	@Override
	public void onGenericRemoteSignal( SocketTileAccess ts, SideConfig config, ForgeDirection side ) {
		int on = config.meta & 7;
		int setting = config.meta >> 3;
		setting &= 7;
		int time = config.meta >> 6;
		int reBuild = 0;

		setting++;

		if( setting >= 8 ) setting = 0;

		reBuild = 0;
		reBuild <<= 3;
		reBuild |= setting;
		reBuild <<= 3;
		reBuild |= on;

		config.meta = reBuild;

		ts.sendClientSideState( side.ordinal() );
	}
}
