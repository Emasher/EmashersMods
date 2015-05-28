package emasher.gas.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import emasher.gas.EmasherGas;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

import java.util.Random;

public class BlockWeaponizedGas extends BlockGasGeneric {
	public BlockWeaponizedGas() {
		super( 50, false, true, true );
	}
	
	@Override
	@SideOnly( Side.CLIENT )
	public void registerBlockIcons( IIconRegister ir ) {
		this.blockIcon = ir.registerIcon( "gascraft:toxicGas" );
	}

	@Override
	public void onEntityCollidedWithBlock( World par1World, int par2, int par3, int par4, Entity ent ) {
		if( !par1World.isRemote ) {
			Random rand = par1World.rand;
			Item helmet = null;
			int helmetIndex = 3;
			if( ent instanceof EntityPlayer ) {
				if( ( ( EntityPlayer ) ent ).inventory.armorItemInSlot( helmetIndex ) != null ) {
					helmet = ( ( EntityPlayer ) ent ).inventory.armorItemInSlot( helmetIndex ).getItem();
				}
			}
			
			if( ent instanceof EntityPlayer && helmet == EmasherGas.gasMask ) {
				ItemStack helmStack = ( ( EntityPlayer ) ent ).inventory.armorItemInSlot( helmetIndex );
				
				if( rand.nextInt( 10 ) == 0 ) {
					helmStack.damageItem( 1, ( EntityLivingBase ) ent );
				}
				
				if( helmStack.getItemDamage() >= helmStack.getMaxDamage() ) {
					( ( EntityPlayer ) ent ).inventory.armorInventory[helmetIndex] = null;
				}
				
			} else if( ent instanceof EntityLivingBase ) {
				if( ( ( EntityLivingBase ) ent ).getHealth() > 6 ) {
					( ( EntityLivingBase ) ent ).addPotionEffect( new PotionEffect( 7, 1 ) );
				}
				( ( EntityLivingBase ) ent ).addPotionEffect( new PotionEffect( 19, 1000 ) );
				( ( EntityLivingBase ) ent ).addPotionEffect( new PotionEffect( 9, 1000 ) );
			}
		}
		
	}
}
