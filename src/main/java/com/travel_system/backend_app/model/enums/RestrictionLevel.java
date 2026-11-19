package com.travel_system.backend_app.model.enums;

/*
 * níveis de bloqueio
 * os bloqueios são sequenciais> bloqueia os do lvl antigo + dos próprios
 * 'none' não bloqueia nada
 * */

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum RestrictionLevel {
    NONE(0),
    LEVEL_1(1, AccessCategory.TRAVEL_BOARDING),
    LEVEL_2(2, AccessCategory.TRANSPORT_OPERATIONS),
    LEVEL_3(3, AccessCategory.DOCUMENTS);

    private final int value;
    private final Set<AccessCategory> addedCategories;

    RestrictionLevel(int value, AccessCategory... addedCategories) {
        this.value = value;
        this.addedCategories = addedCategories.length == 0
                ? EnumSet.noneOf(AccessCategory.class)
                : EnumSet.copyOf(Arrays.asList(addedCategories));
    }

    public int getValue() {
        return value;
    }

    public Set<AccessCategory> getAddedCategories() {
        return addedCategories;
    }

    public static RestrictionLevel fromValue(int v) {
        return Arrays.stream(values())
                .filter(lvl -> lvl.getValue() == v)
                .findFirst()
                .orElse(NONE);
   }

   // junta as categs desse nível e dos anteriores
   public EnumSet<AccessCategory> blockedCategories() {
       EnumSet<AccessCategory> blocked = EnumSet.noneOf(AccessCategory.class);

       for (RestrictionLevel lvl : values()) {
           if (lvl.value <= this.value) {
               blocked.addAll(lvl.addedCategories);
           }
       }

       return blocked;
   }

   public boolean blocks(AccessCategory category) {
        return blockedCategories().contains(category);
   }

    // todas as categorias que em algum nível bloqueiam (usado no verifier)
    public static Set<AccessCategory> allBlockableCategories() {
        EnumSet<AccessCategory> all = EnumSet.noneOf(AccessCategory.class);

        for (RestrictionLevel lvl : values()) {
            all.addAll(lvl.addedCategories);
        }

        return all;
    }

}
