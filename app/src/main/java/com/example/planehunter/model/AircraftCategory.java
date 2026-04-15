package com.example.planehunter.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.planehunter.R;

import java.util.ArrayList;
import java.util.List;

public final class AircraftCategory {
	public static final long UNKNOWN = 100L;
	public static final long AIRLINER = 101L;
	public static final long CARGO = 102L;
	public static final long BUSINESS_JET = 103L;
	public static final long GENERAL_AVIATION = 104L;
	public static final long TURBOPROP_REGIONAL = 105L;
	public static final long HELICOPTER = 106L;
	public static final long MILITARY_GOVERNMENT = 107L;

	private AircraftCategory() {
	}

	@NonNull
	public static ArrayList<Long> getDefaultAlertCategories() {
		ArrayList<Long> categories = new ArrayList<>();
		categories.add(AIRLINER);
		categories.add(CARGO);
		categories.add(BUSINESS_JET);
		categories.add(GENERAL_AVIATION);
		categories.add(TURBOPROP_REGIONAL);
		categories.add(HELICOPTER);
		categories.add(MILITARY_GOVERNMENT);
		return categories;
	}

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

	@NonNull
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

	public static int getImageResId(long category) {
		if (category == AIRLINER) return R.drawable.plane_airliner;
		if (category == CARGO) return R.drawable.plane_cargo;
		if (category == BUSINESS_JET) return R.drawable.plane_business_jet;
		if (category == GENERAL_AVIATION) return R.drawable.plane_turboprop;
		if (category == TURBOPROP_REGIONAL) return R.drawable.plane_turboprop;
		if (category == HELICOPTER) return R.drawable.plane_helicopter;
		if (category == MILITARY_GOVERNMENT) return R.drawable.plane_military;
		return R.drawable.air_plan;
	}
}