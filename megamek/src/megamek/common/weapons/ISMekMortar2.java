/**
 * MegaMek - Copyright (C) 2005 Ben Mazur (bmazur@sev.org)
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 */
package megamek.common.weapons;

import megamek.common.TechConstants;

/**
 * @author Jason Tighe
 */
public class ISMekMortar2 extends MekMortarWeapon {

    /**
     *
     */
    private static final long serialVersionUID = -6644886866545312980L;

    /**
     *
     */
    public ISMekMortar2() {
        super();

        name = "'Mech Mortar 2";
        setInternalName("IS Mech Mortar-2");
        addLookupName("ISMekMortar2");
        addLookupName("IS Mek Mortar 2");
        rackSize = 2;
        minimumRange = 6;
        shortRange = 7;
        mediumRange = 14;
        longRange = 21;
        extremeRange = 28;
        bv = 14;
        heat = 2;
        criticals = 2;
        tonnage = 5;
        cost = 15000;
        introDate = 2521;
        extinctDate = 2819;
        reintroDate = 3043;
        techLevel.put(2521, TechConstants.T_IS_EXPERIMENTAL);
        techLevel.put(2531, TechConstants.T_IS_ADVANCED);
        techLevel.put(3052, TechConstants.T_IS_TW_NON_BOX);
        availRating = new int[] { RATING_D ,RATING_F ,RATING_F ,RATING_E};
        techRating = RATING_B;
        rulesRefs = "263, TM";
    }
}
