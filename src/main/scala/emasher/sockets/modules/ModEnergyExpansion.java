package emasher.sockets.modules;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import emasher.api.SideConfig;
import emasher.api.SocketModule;
import emasher.api.SocketTileAccess;
import emasher.core.EmasherCore;
import emasher.sockets.SocketsMod;

public class ModEnergyExpansion extends SocketModule
{
	public ModEnergyExpansion(int id)
	{
		super(id, "sockets:energy_expansion", "sockets:energy_expansion_in", "sockets:energy_expansion_out");
	}

	@Override
	public String getLocalizedName()
	{
		return "Energy Storage Upgrade";
	}
	
	@Override
	public void getToolTip(List l)
	{
		l.add("Adds 1 000 000 RF");
		l.add("of extra energy storage");
	}
	
	@Override
	public void getIndicatorKey(List l)
	{
		l.add(SocketsMod.PREF_WHITE + "Configure if input or output or neither");
	}
	
	@Override
	public void addRecipe()
	{
		GameRegistry.addShapedRecipe(new ItemStack(SocketsMod.module, 1, moduleID), "pdp", "ggg", "pbp", Character.valueOf('g'), Items.gold_ingot, Character.valueOf('p'), EmasherCore.psu,
                Character.valueOf('d'), Items.diamond, Character.valueOf('b'), SocketsMod.blankSide);
	}

    @SideOnly(Side.CLIENT)
    public String getInternalTexture(SocketTileAccess ts, SideConfig config, ForgeDirection side) { return "sockets:inner_grey_tile"; }

    @SideOnly(Side.CLIENT)
    public String[] getAllInternalTextures() { return new String[] {"sockets:inner_grey_tile"}; }
	
	@Override
	public int getCurrentTexture(SideConfig config) { return config.meta; }
	
	@Override
	public boolean hasRSIndicator() { return true; }
	
	@Override
	public boolean hasLatchIndicator() { return true; }
	
	@Override
	public boolean isEnergyInterface(SideConfig config) { return config.meta != 0; }
	
	@Override
	public void onGenericRemoteSignal(SocketTileAccess ts, SideConfig config, ForgeDirection side)
	{
		config.meta++;
		if(config.meta == 3) config.meta = 0;
		ts.sendClientSideState(side.ordinal());
		ts.updateAdj(side);
	}
	
	@Override
	public void updateSide(SideConfig config, SocketTileAccess ts, ForgeDirection side)
	{
		
		boolean allOff = true;
		if(config.meta == 2)
		{
			for(int i = 0; i < 3; i++)
			{
				if(config.rsControl[i])
				{
					if(ts.getRSControl(i))
					{
						ts.outputEnergy(1000, side);
						return;
					}
					allOff = false;
				}
				
				if(config.rsLatch[i])
				{
					if(ts.getRSLatch(i))
					{
						ts.outputEnergy(1000, side);
						return;
					}
					allOff = false;
				}
			}
			
			if(allOff)
			{
				ts.outputEnergy(1000, side);
				
			}
		}
			
	}
	
	@Override
	public int receiveEnergy(int amount, boolean simulate, SideConfig config, SocketTileAccess ts)
	{
		boolean allOff = true;
		if(config.meta == 1)
		{
			for(int i = 0; i < 3; i++)
			{
				if(config.rsControl[i])
				{
					if(ts.getRSControl(i))
					{
						return ts.addEnergy(amount, simulate);
					}
					allOff = false;
				}
				
				if(config.rsLatch[i])
				{
					if(ts.getRSLatch(i))
					{
						return ts.addEnergy(amount, simulate);
					}
					allOff = false;
				}
			}
			
			if(allOff)
			{
				return ts.addEnergy(amount, simulate);
				
			}
		}
		
		return 0;
	}
	
	@Override
	public int extractEnergy(int amount, boolean simulate, SideConfig config, SocketTileAccess ts)
	{
		boolean allOff = true;
		if(config.meta == 2)
		{
			for(int i = 0; i < 3; i++)
			{
				if(config.rsControl[i])
				{
					if(ts.getRSControl(i))
					{
						return ts.useEnergy(amount, simulate);
					}
					allOff = false;
				}
				
				if(config.rsLatch[i])
				{
					if(ts.getRSLatch(i))
					{
						return ts.useEnergy(amount, simulate);
					}
					allOff = false;
				}
			}
			
			if(allOff)
			{
				return ts.addEnergy(amount, simulate);
				
			}
		}
		
		return 0;
	}
	
	@Override
	public void init(SocketTileAccess ts, SideConfig config, ForgeDirection side)
	{
		ts.setMaxEnergyStored(ts.getMaxEnergyStored() + 1000000);
	}
	
	@Override
	public void onRemoved(SocketTileAccess ts, SideConfig config, ForgeDirection side)
	{
		ts.setMaxEnergyStored(ts.getMaxEnergyStored() - 1000000);
	}
}
