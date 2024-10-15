package com.kai.ctt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompactTrimTooltipClient implements ClientModInitializer {

	public static boolean experimental_all_language_support = true;

	private static final Set<String> LANGUAGES = new HashSet<>(Arrays.asList(
			"en_au",
			"en_ca",
			"en_gb",
			"en_nz",
			"en_us"
	));

	@Override
	public void onInitializeClient() {
		// Load config
		try {
			List<String> lines = Files.readAllLines(Paths.get("config/CompactTrimTooltip.toml"));
			for (String line : lines) {
				if (line.startsWith("experimental_all_language_support")) {
					experimental_all_language_support = Boolean.parseBoolean(line.split("=")[1].strip());
				}
			}
		}
		catch (IOException e) {
			System.out.println("[Compact Trim Tooltip] Couldn't read config! Error: " + e.getMessage());
		}

		// Config command
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("CompactTrimTooltip").executes(context -> {
			experimental_all_language_support = !experimental_all_language_support;
			context.getSource().sendFeedback(Text.of("[Compact Trim Tooltip] Support for all languages now " + (experimental_all_language_support ? "ENABLED" : "DISABLED")));
			try {
				Files.createDirectories(Paths.get("config"));
				Files.write(Paths.get("config/CompactTrimTooltip.toml"), ("experimental_all_language_support = " + experimental_all_language_support).getBytes());
			}
			catch (IOException e) {
				System.out.println("[Compact Trim Tooltip] Couldn't save config! Error: " + e.getMessage());
				context.getSource().sendFeedback(Text.of("[Compact Trim Tooltip] Couldn't save config!"));
			}
			return 1;
		})));

		// Tooltip stuff
		ItemTooltipCallback.EVENT.register((ItemStack stack, Item.TooltipContext context, TooltipType tooltipType, List<Text> lines) -> {
			if (lines.size() >= 3) {
				Text line1 = lines.get(1);

				// If translatable get big thing with translation key in it
				if (line1.getContent() instanceof TranslatableTextContent) {
					TranslatableTextContent line1key = (TranslatableTextContent) line1.getContent();

					// Get key from big thing and compare it
					if (line1key.getKey().equals("item.minecraft.smithing_template.upgrade")) {
						Text pattern = lines.get(2);
						Text material = lines.get(3);

						String patternString = pattern.getString().strip();
						String materialString = material.getString().strip();

						// Remove "material" after the actual material name depending on language
						// If client language is not english check last word or first word (things like "material of redstone") for "mater", since a lot of languages follow those patterns
						if (LANGUAGES.contains(MinecraftClient.getInstance().options.language)) {
							String[] materialStringSplit = materialString.split(" ");
							materialString = String.join(" ", Arrays.copyOf(materialStringSplit, materialStringSplit.length - 1)).strip();
						}
						else if (experimental_all_language_support) {
							String[] materialStringSplit = materialString.split(" ");
							if (materialStringSplit.length >= 2) {
								if (!materialStringSplit[0].toLowerCase().startsWith("mater") && materialStringSplit[materialStringSplit.length - 1].toLowerCase().startsWith("mater")) {
									materialString = String.join(" ", Arrays.copyOf(materialStringSplit, materialStringSplit.length - 1)).strip();
								}
								else if (materialStringSplit.length >= 3 && materialStringSplit[0].toLowerCase().startsWith("mater") && !materialStringSplit[materialStringSplit.length - 1].toLowerCase().startsWith("mater")) {
									materialString = String.join(" ", Arrays.copyOfRange(materialStringSplit, 2, materialStringSplit.length)).strip();
									materialString = materialString.substring(0, 1).toUpperCase() + materialString.substring(1);
								}
							}
						}

						// The lines have a different structure, so pattern/material needs .getSiblings().get(0) to access .getStlye(), but not for .getString() smh?
						// Probably cuz .getString() gets the whole line, which could have different styles like I'm doing with MutableText
						// line1 doesn't have Siblings, so no need for .getSiblings().get(0) to get the style

						// Assemble new compact trim tooltip line
						MutableText compact = Text.empty()
								.append(Text.literal(patternString + " ").setStyle(line1.getStyle()))
								.append(Text.literal("(" + materialString + ")").setStyle(material.getSiblings().get(0).getStyle()));

						// Change the first line to the newly assembled compact one and remove the following two
						lines.set(1, compact);
						lines.remove(2);
						lines.remove(2); // line 2 again, cuz everything moves up when deleting a line
					}
				}
			}
		});
	}
}