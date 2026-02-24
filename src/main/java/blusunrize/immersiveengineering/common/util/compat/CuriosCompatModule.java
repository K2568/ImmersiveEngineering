/*
 * BluSunrize
 * Copyright (c) 2021
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.util.compat;

import blusunrize.immersiveengineering.api.tool.IElectricEquipment;
import blusunrize.immersiveengineering.common.items.EarmuffsItem;
import blusunrize.immersiveengineering.common.items.PowerpackItem;
import blusunrize.immersiveengineering.common.util.compat.IECompatModules.StandardIECompatModule;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.InterModComms;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.SlotTypePreset;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Predicate;

public class CuriosCompatModule extends StandardIECompatModule
{
	@Override
	public void sendIMCs()
	{
		InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
				() -> SlotTypePreset.BACK.getMessageBuilder().build());
		InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
				() -> SlotTypePreset.HEAD.getMessageBuilder().build());
		PowerpackItem.POWERPACK_GETTER.addGetter(living -> getCuriosIfVisible(
				living, SlotTypePreset.BACK, stack -> stack.getItem() instanceof PowerpackItem
		));
		EarmuffsItem.EARMUFF_GETTERS.addGetter(living -> getCuriosIfVisible(
				living, SlotTypePreset.HEAD, stack -> stack.getItem() instanceof EarmuffsItem
		));
		// Register Curios integration for IElectricEquipment: any item implementing IElectricEquipment
		// that is equipped in a Curios slot will have onStrike called during electric damage checks.
		// EquipmentSlot.CHEST is used as a placeholder since Curios slots don't map 1:1 to vanilla slots.
		IElectricEquipment.ADDITIONAL_STRIKE_HANDLERS.add((entity, cache, dmg, source) ->
				applyElectricToEntityCurios(entity, cache, dmg, source));
	}

	public static ItemStack getCuriosIfVisible(LivingEntity living, SlotTypePreset slot, Predicate<ItemStack> predicate)
	{
		LazyOptional<ICuriosItemHandler> optional = CuriosApi.getCuriosHelper().getCuriosHandler(living);
		return optional.resolve()
				.flatMap(handler -> handler.getStacksHandler(slot.getIdentifier()))
				.filter(ICurioStacksHandler::isVisible)
				.map(stacksHandler -> {
					for(int i = 0; i < stacksHandler.getSlots(); i++)
						if(stacksHandler.getRenders().get(i))
						{
							ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
							if(predicate.test(stack))
								return stack;
						}
					return ItemStack.EMPTY;
				}).orElse(ItemStack.EMPTY);
	}

	/**
	 * Iterates all equipped Curios stacks on {@code entity} and calls
	 * {@link IElectricEquipment#onStrike} for each stack whose item implements {@link IElectricEquipment}.
	 * {@link EquipmentSlot#CHEST} is used as the slot placeholder for all Curios slots, since Curios
	 * slots do not have a guaranteed 1-to-1 mapping to vanilla {@link EquipmentSlot} values.
	 */
	private static void applyElectricToEntityCurios(LivingEntity entity, Map<String, Object> cache,
													@Nullable DamageSource dmg,
													IElectricEquipment.ElectricSource source)
	{
		CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve().ifPresent(handler ->
				handler.getCurios().forEach((slotId, stacksHandler) -> {
					for(int i = 0; i < stacksHandler.getSlots(); i++)
					{
						ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
						if(!stack.isEmpty()&&stack.getItem() instanceof IElectricEquipment electricItem)
							electricItem.onStrike(stack, EquipmentSlot.CHEST, entity, cache, dmg, source);
					}
				}));
	}

}