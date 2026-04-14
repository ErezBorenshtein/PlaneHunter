package com.example.planehunter.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.planehunter.model.AircraftCategory;

import java.util.Locale;

public final class AircraftCategoryClassifier {

    private AircraftCategoryClassifier() {
    }

    public static long classify(
            @Nullable String typeCode,
            @Nullable String typeName,
            @Nullable String ownerOperator,
            boolean isMilitary
    ) {
        String tc = normalize(typeCode);
        String tn = normalize(typeName);
        String oo = normalize(ownerOperator);

        if (isMilitary) {
            return AircraftCategory.MILITARY_GOVERNMENT;
        }

        if (isHelicopter(tc, tn)) {
            return AircraftCategory.HELICOPTER;
        }

        if (isCargo(tc, tn, oo)) {
            return AircraftCategory.CARGO;
        }

        if (isBusinessJet(tc, tn)) {
            return AircraftCategory.BUSINESS_JET;
        }

        if (isTurbopropOrRegional(tc, tn)) {
            return AircraftCategory.TURBOPROP_REGIONAL;
        }

        if (isGeneralAviation(tc, tn)) {
            return AircraftCategory.GENERAL_AVIATION;
        }

        if (isAirliner(tc, tn)) {
            return AircraftCategory.AIRLINER;
        }

        return AircraftCategory.UNKNOWN;
    }

    private static boolean isHelicopter(@NonNull String typeCode, @NonNull String typeName) {
        return containsAny(typeName,
                "HELICOPTER",
                "EUROCOPTER",
                "AIRBUS HELICOPTERS",
                "ROBINSON",
                "SIKORSKY",
                "AGUSTA",
                "LEONARDO HELICOPTER",
                "BELL ")
                || startsWithAny(typeCode,
                "EC", "AS3", "B06", "R22", "R44", "H47", "UH1", "S76", "A109", "AW");
    }

    private static boolean isCargo(@NonNull String typeCode, @NonNull String typeName, @NonNull String ownerOperator) {
        if (containsAny(typeName, "FREIGHTER", "CARGO")) {
            return true;
        }

        if (containsAny(ownerOperator,
                "FEDEX",
                "UPS",
                "DHL",
                "CARGOLUX",
                "KALITTA",
                "ATLAS AIR",
                "QATAR CARGO",
                "EMIRATES SKYCARGO",
                "CHALLENGE AIR CARGO")) {
            return true;
        }

        return endsWithAny(typeCode, "F")
                || startsWithAny(typeCode, "B77F", "B744F", "B748F", "A332F", "A333F", "MD11F", "B734F", "B738F");
    }

    private static boolean isBusinessJet(@NonNull String typeCode, @NonNull String typeName) {
        return containsAny(typeName,
                "GULFSTREAM",
                "LEARJET",
                "FALCON",
                "CITATION",
                "CHALLENGER",
                "GLOBAL",
                "HAWKER",
                "PHENOM",
                "LEGACY")
                || startsWithAny(typeCode,
                "GL", "G2", "G3", "G4", "G5", "G6",
                "LJ", "CL", "FA", "C25", "C5", "E55P", "PRM1");
    }

    private static boolean isTurbopropOrRegional(@NonNull String typeCode, @NonNull String typeName) {
        return containsAny(typeName,
                "ATR",
                "DASH 8",
                "Q400",
                "SAAB 340",
                "SAAB 2000",
                "BEECH 1900",
                "TURBOPROP")
                || startsWithAny(typeCode,
                "AT4", "AT5", "AT7", "AT8",
                "DH8", "SF3", "J31", "J32", "J41", "B190", "E120", "F50");
    }

    private static boolean isGeneralAviation(@NonNull String typeCode, @NonNull String typeName) {
        return containsAny(typeName,
                "CESSNA",
                "PIPER",
                "CIRRUS",
                "DIAMOND",
                "ROBIN",
                "MOONEY",
                "BONANZA",
                "BEECHCRAFT")
                || startsWithAny(typeCode,
                "C15", "C17", "C18", "C20", "C21", "C23", "C24", "C30",
                "PA2", "PA3", "SR2", "DA4", "BE3", "M20");
    }

    private static boolean isAirliner(@NonNull String typeCode, @NonNull String typeName) {
        return containsAny(typeName,
                "AIRBUS A2",
                "AIRBUS A3",
                "BOEING 7",
                "EMBRAER 17",
                "EMBRAER 19",
                "EMBRAER 29",
                "A220",
                "MD-8",
                "MD-9")
                || startsWithAny(typeCode,
                "A19", "A20", "A21", "A22", "A30", "A31", "A32", "A33", "A34", "A35", "A38",
                "B71", "B72", "B73", "B74", "B75", "B76", "B77", "B78",
                "E17", "E18", "E19", "E29", "BCS");
    }

    private static boolean startsWithAny(@NonNull String source, @NonNull String... prefixes) {
        for (String prefix : prefixes) {
            if (source.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithAny(@NonNull String source, @NonNull String... suffixes) {
        for (String suffix : suffixes) {
            if (source.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(@NonNull String source, @NonNull String... values) {
        for (String value : values) {
            if (source.contains(value)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.US);
    }
}