package com.example.planehunter.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines aircraft categories and provides utility methods for handling them.
 * Uses long constants instead of Enums for easier Firebase integration.
 */
public final class AircraftCategory {

	/** Category ID for unknown aircraft types. */
	public static final long UNKNOWN = 100L;
	/** Category ID for commercial airliners. */
	public static final long AIRLINER = 101L;
	/** Category ID for cargo/freight aircraft. */
	public static final long CARGO = 102L;
	/** Category ID for private or business jets. */
	public static final long BUSINESS_JET = 103L;
	/** Category ID for general aviation aircraft (e.g., Cessnas). */
	public static final long GENERAL_AVIATION = 104L;
	/** Category ID for turboprop and regional aircraft. */
	public static final long TURBOPROP_REGIONAL = 105L;
	/** Category ID for helicopters. */
	public static final long HELICOPTER = 106L;
	/** Category ID for military or government aircraft. */
	public static final long MILITARY_GOVERNMENT = 107L;

	private AircraftCategory() {
	}

	/**
	 * Returns the default list of categories for which alerts are enabled.
	 * @return A list containing HELICOPTER and MILITARY_GOVERNMENT.
	 */
	@NonNull
	public static ArrayList<Long> getDefaultAlertCategories() {
		ArrayList<Long> categories = new ArrayList<>();
		categories.add(HELICOPTER);
		categories.add(MILITARY_GOVERNMENT);
		return categories;
	}

	/**
	 * Normalizes a list of category IDs, ensuring they are valid and known.
	 * Returns defaults if the list is empty or invalid.
	 * @param rawCategories The input list of category IDs.
	 * @return A normalized list of category IDs.
	 */
	@NonNull
	public static ArrayList<Long> normalizeAlertCategories(@Nullable List<Long> rawCategories) {
		if (rawCategories == null || rawCategories.isEmpty()) {
			return getDefaultAlertCategories();
		}

		boolean hasNewCategory = false;
		for (Long category : rawCategories) {
			if (category == null) {
				continue;
			}

			if (isKnownNewCategory(category)) {
				hasNewCategory = true;
				break;
			}
		}

		if (!hasNewCategory) {
			return getDefaultAlertCategories();
		}

		ArrayList<Long> result = new ArrayList<>();
		for (Long category : rawCategories) {
			if (category == null) {
				continue;
			}

			if (isKnownNewCategory(category) && !result.contains(category)) {
				result.add(category);
			}
		}

		if (result.isEmpty()) {
			return getDefaultAlertCategories();
		}

		return result;
	}

	/**
	 * Checks if a given category ID is valid.
	 * @param category The category ID to check.
	 * @return true if valid, false otherwise.
	 */
	public static boolean isKnownNewCategory(long category) {
		return category == AIRLINER
				|| category == CARGO
				|| category == BUSINESS_JET
				|| category == GENERAL_AVIATION
				|| category == TURBOPROP_REGIONAL
				|| category == HELICOPTER
				|| category == MILITARY_GOVERNMENT
				|| category == UNKNOWN;
	}

	/**
	 * Returns the user-friendly display name for a given category.
	 * @param category The category ID.
	 * @return The display name string.
	 */
	public static String getDisplayName(long category) {
		if (category == AIRLINER) return "Airliner";
		if (category == CARGO) return "Cargo";
		if (category == BUSINESS_JET) return "Business Jet";
		if (category == GENERAL_AVIATION) return "General Aviation";
		if (category == TURBOPROP_REGIONAL) return "Turboprop / Regional";
		if (category == HELICOPTER) return "Helicopter";
		if (category == MILITARY_GOVERNMENT) return "Military / Government";
		return "Unknown";
	}
}