/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.layneburns.farmingruns.data.DataLoader;
import com.layneburns.farmingruns.data.GameData;
import com.layneburns.farmingruns.data.Pack;
import com.layneburns.farmingruns.state.ItemLedger;
import com.layneburns.farmingruns.state.ItemSource;
import com.layneburns.farmingruns.state.StorageSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class PaymentKitTest
{
	private static GameData data;
	private static Pack oranges;
	private static Pack apples;
	private static Pack onions;
	private static int basket;
	private static int sack;

	@BeforeClass
	public static void load()
	{
		data = new DataLoader(new Gson()).load();
		oranges = pack("Oranges(5)");
		apples = pack("Apples(5)");
		onions = pack("Onions(10)");
		basket = data.getItems().group("basket").iterator().next();
		sack = data.getItems().group("empty_sack").iterator().next();
	}

	private static Pack pack(String name)
	{
		return data.getItems().packs().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
	}

	private static ItemLedger bank(Map<Integer, Integer> items)
	{
		return ItemLedger.EMPTY.with(ItemSource.BANK, new StorageSnapshot(items, 1));
	}

	private static int take(PaymentKit.Plan plan, int itemId)
	{
		return plan.getTrip().stream().filter(r -> r.getItemIds().contains(itemId)).mapToInt(Requirement::getAmount)
			.findFirst().orElse(0);
	}

	@Test
	public void makesAllYouCanAndTakesAnInventoryAtATime()
	{
		PaymentKit.Plan plan = PaymentKit.plan(List.of(oranges),
			bank(Map.of(oranges.getProduceId(), 37, basket, 10)), data.getItems());
		assertEquals(7, (int) plan.getMade().get(oranges));
		// 4 baskets and their 20 oranges fill 24 of the 28 slots.
		assertEquals(4, take(plan, basket));
		assertEquals(20, take(plan, oranges.getProduceId()));
		assertTrue(plan.getContainersShort().isEmpty());
	}

	@Test
	public void partFilledOnesAreToppedUpFirst()
	{
		// 6 oranges: 1 tops up Oranges(4), 2 top up Oranges(3); the 3 left aren't enough for an empty basket.
		PaymentKit.Plan plan = PaymentKit.plan(List.of(oranges), bank(Map.of(oranges.getProduceId(), 6,
			oranges.fillId(4), 1, oranges.fillId(3), 1, basket, 5)), data.getItems());
		assertEquals(2, (int) plan.getMade().get(oranges));
		assertEquals(1, take(plan, oranges.fillId(4)));
		assertEquals(1, take(plan, oranges.fillId(3)));
		assertEquals(3, take(plan, oranges.getProduceId()));
		assertEquals(0, take(plan, basket));
	}

	@Test
	public void higherPaymentsGetTheBasketsWhenThereArentEnough()
	{
		PaymentKit.Plan plan = PaymentKit.plan(List.of(oranges, apples), bank(Map.of(oranges.getProduceId(), 30,
			apples.getProduceId(), 20, basket, 5)), data.getItems());
		assertEquals(5, (int) plan.getMade().get(oranges));
		assertEquals(0, (int) plan.getMade().get(apples));
		// 5 oranges and 20 apples left over: 5 more baskets would pack them.
		assertEquals(Map.of("Basket", 5), plan.getContainersShort());
		assertEquals(0, take(plan, apples.getProduceId()));
	}

	@Test
	public void aTripMixesBasketsAndSacksToFillTheInventory()
	{
		// What you carry counts too.
		ItemLedger ledger = bank(Map.of(oranges.getProduceId(), 10, basket, 2, sack, 2))
			.with(ItemSource.INVENTORY, new StorageSnapshot(Map.of(onions.getProduceId(), 20), 1));
		PaymentKit.Plan plan = PaymentKit.plan(List.of(oranges, onions), ledger, data.getItems());
		assertEquals(2, (int) plan.getMade().get(onions));
		// 2 baskets of oranges take 12 slots; one sack of onions (11) fits in the 16 left, a second doesn't.
		assertEquals(2, take(plan, basket));
		assertEquals(10, take(plan, oranges.getProduceId()));
		assertEquals(1, take(plan, sack));
		assertEquals(10, take(plan, onions.getProduceId()));
	}

	@Test
	public void nothingToMakeWithoutProduce()
	{
		PaymentKit.Plan plan = PaymentKit.plan(List.of(oranges), bank(Map.of(basket, 10)), data.getItems());
		assertEquals(0, (int) plan.getMade().get(oranges));
		assertTrue(plan.getTrip().isEmpty());
		assertNull(plan.getContainersShort().get("Basket"));
	}

	@Test
	public void eachPaymentSaysWhatItPaysFor()
	{
		assertEquals(List.of("Maple"), PaymentKit.paysFor(oranges, data.seeds()));
		List<String> forApples = PaymentKit.paysFor(apples, data.seeds());
		assertTrue(forApples.toString(), forApples.containsAll(List.of("Strawberry", "Willow", "Banana tree")));
		assertTrue(PaymentKit.paysFor(pack("Tomatoes(5)"), data.seeds()).contains("Acorn"));
	}
}
