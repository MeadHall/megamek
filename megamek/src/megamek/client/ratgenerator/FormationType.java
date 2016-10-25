/**
 * 
 */
package megamek.client.ratgenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import megamek.common.AmmoType;
import megamek.common.EntityMovementMode;
import megamek.common.EntityWeightClass;
import megamek.common.EquipmentMode;
import megamek.common.EquipmentType;
import megamek.common.MechSummary;
import megamek.common.MiscType;
import megamek.common.UnitType;
import megamek.common.WeaponType;
import megamek.common.weapons.ACWeapon;
import megamek.common.weapons.ArtilleryWeapon;
import megamek.common.weapons.LBXACWeapon;
import megamek.common.weapons.LRMWeapon;
import megamek.common.weapons.SRMWeapon;
import megamek.common.weapons.TAGWeapon;
import megamek.common.weapons.UACWeapon;

/**
 * Campaign Operations rules for force generation.
 * 
 * @author Neoancient
 *
 */
public class FormationType {
    
    public enum UnitRole {
        UNDETERMINED (false),
        AMBUSHER (true),
        BRAWLER (true),
        JUGGERNAUT (true),
        MISSILE_BOAT (true),
        SCOUT (true),
        SKIRMISHER (true),
        SNIPER (true),
        STRIKER (true),
        ATTACK_FIGHTER (false),
        DOGFIGHTER (false),
        FAST_DOGFIGHTER (false),
        FIRE_SUPPORT (false),
        INTERCEPTOR (false),
        TRANSPORT (false);
        
        private boolean ground;
        
        UnitRole(boolean ground) {
            this.ground = ground;
        }
        
        public boolean isGroundRole() {
            return ground;
        }
        
        public static UnitRole parseRole(String role) {
            switch (role.toLowerCase()) {
            case "ambusher":
                return AMBUSHER;
            case "brawler":
                return BRAWLER;
            case "juggernaut":
                return JUGGERNAUT;
            case "missile_boat":
            case "missile boat":
                return MISSILE_BOAT;
            case "scout":
                return SCOUT;
            case "skirmisher":
                return SKIRMISHER;
            case "sniper":
                return SNIPER;
            case "striker":
                return STRIKER;
            case "attack_fighter":
            case "attack figher":
            case "attack":
                return ATTACK_FIGHTER;
            case "dogfighter":
                return DOGFIGHTER;
            case "fast_dogfighter":
            case "fast dogfighter":
                return FAST_DOGFIGHTER;
            case "fire_support":
            case "fire support":
            case "fire-support":
                return FIRE_SUPPORT;
            case "interceptor":
                return INTERCEPTOR;
            case "transport":
                return TRANSPORT;
            default:
                System.err.println("Could not parse AS Role " + role);
                return UNDETERMINED;
            }
        }        
    };
    
    private static HashMap<String,FormationType> allFormationTypes = null;
    public static FormationType getFormationType(String key) {
        if (allFormationTypes == null) {
            createFormationTypes();
        }
        return allFormationTypes.get(key);
    }
    
    private String name = "Support";
    // Some formation types allow units not normally generated for general combat roles (e.g. artillery, cargo)  
    private EnumSet<MissionRole> missionRoles = EnumSet.noneOf(MissionRole.class);
    // If all units in the force have this role, other constraints can be ignored.
    private UnitRole idealRole = UnitRole.UNDETERMINED;
    
    private int minWeightClass = 0;
    private int maxWeightClass = EntityWeightClass.SIZE;
    // Used as a filter when generating units
    private Predicate<MechSummary> mainCriteria = ms -> true;
    // Additional criteria that have to be fulfilled by a portion of the force
    private List<Constraint> otherCriteria = new ArrayList<>();
    
    public String getName() {
        return name;
    }
    
    public UnitRole getIdealRole() {
        return idealRole;
    }

    public int getMinWeightClass() {
        return minWeightClass;
    }

    public int getMaxWeightClass() {
        return maxWeightClass;
    }

    public Predicate<MechSummary> getMainCriteria() {
        return mainCriteria;
    }

    public List<Constraint> getOtherCriteria() {
        return otherCriteria;
    }
    
    private static UnitRole getUnitRole(MechSummary ms) {
        ModelRecord mRec = RATGenerator.getInstance().getModelRecord(ms.getName());
        return mRec == null? UnitRole.UNDETERMINED : mRec.getUnitRole();
    }
    
    private static Set<MissionRole> getMissionRoles(MechSummary ms) {
        ModelRecord mRec = RATGenerator.getInstance().getModelRecord(ms.getName());
        return mRec == null? EnumSet.noneOf(MissionRole.class) : mRec.getRoles();
    }
    
    private static int getDamageAtRange(MechSummary ms, int range) {
        int retVal = 0;
        for (int i = 0; i < ms.getEquipmentNames().size(); i++) {
            if (EquipmentType.get(ms.getEquipmentNames().get(i)) instanceof WeaponType) {
                final WeaponType weapon = (WeaponType)EquipmentType.get(ms.getEquipmentNames().get(i));
                if (weapon.getLongRange() < range) {
                    continue;
                }
                int damage = 0;
                if (weapon.getAmmoType() != AmmoType.T_NA) {
                    Optional<EquipmentType> ammo = ms.getEquipmentNames().stream()
                        .map(name -> EquipmentType.get(name))
                        .filter(eq -> eq instanceof AmmoType
                                && ((AmmoType)eq).getAmmoType() == weapon.getAmmoType()
                                && ((AmmoType)eq).getRackSize() == weapon.getRackSize())
                        .findFirst();
                    if (ammo.isPresent()) {
                        damage = ((AmmoType)ammo.get()).getDamagePerShot()
                                * ((AmmoType)ammo.get()).getRackSize();
                    }
                } else {
                    damage = weapon.getDamage(range);
                }
                if (damage > 0) {
                    retVal += damage * ms.getEquipmentQuantities().get(i);
                }
            }
        }
        return retVal;
    }
    
    public List<MechSummary> generateFormation(FactionRecord faction, int unitType, int year,
            String rating, int size) {
        List<Integer> wcs = new ArrayList<>();
        for (int i = minWeightClass; i <= Math.min(maxWeightClass,
                unitType == UnitType.AERO?EntityWeightClass.WEIGHT_HEAVY : EntityWeightClass.WEIGHT_SUPER_HEAVY); i++) {
            wcs.add(i);
        }
        UnitTable table = UnitTable.findTable(faction, unitType, year, rating, wcs, ModelRecord.NETWORK_NONE,
                EnumSet.noneOf(EntityMovementMode.class), missionRoles, 0);
        if (table == null) {
            return new ArrayList<MechSummary>();
        }
        List<MechSummary> unitList = table.generateUnits(size, ms -> mainCriteria.test(ms));
        if (unitList.isEmpty()) {
            // If we cannot meet the criteria, we may be able to construct a force using just the ideal role.
            if (!idealRole.equals(UnitRole.UNDETERMINED)) {
                unitList = table.generateUnits(size, ms -> getUnitRole(ms).equals(idealRole));
            }
            return unitList;
        }
        
        List<List<MechSummary>> allMatchingUnits = new ArrayList<>();
        
        for (int i = 0; i < otherCriteria.size(); i++) {
            final Constraint constraint = otherCriteria.get(i);
            List<MechSummary> matchingUnits = unitList.stream().filter(ms -> constraint.criterion.test(ms))
                    .collect(Collectors.toList());
            if (matchingUnits.size() < constraint.getMinimum(size)
                    || matchingUnits.size() > constraint.getMaximum(size)) {
                //Sort unitList to put best replacement candidates first
                Collections.sort(unitList, (o1, o2) -> needRating(o1, size) -
                        needRating(o2, size));
                int candidate = 0;
                //Try to replace units until we meet the criterion or we run out of units to replace
                while (candidate < unitList.size()
                        && (matchingUnits.size() < constraint.getMinimum(size)
                                || matchingUnits.size() > constraint.getMaximum(size))) {
                    // Build a unit table filter that includes criteria we've already met but have no extras
                    // For constraints with a max value, use the logical complement of the constraint to keep from going over.
                    List<Predicate<MechSummary>> filter = new ArrayList<>();
                    if (mainCriteria != null) {
                        filter.add(mainCriteria);
                    }
                    for (int j = 0; j < i; j++) {
                        final Constraint other = otherCriteria.get(j);
                        if (other.criterion.test(unitList.get(candidate))
                                && allMatchingUnits.get(j).size() <= other.getMinimum(size)) {
                            filter.add(other.criterion);
                        } else if (!other.criterion.test(unitList.get(candidate))
                                && allMatchingUnits.get(j).size() >= other.getMaximum(size)
                                && other.getMaximum(size) < size) {
                            filter.add(ms -> !other.criterion.test(ms));
                        }
                    }
                    if (matchingUnits.size() > constraint.getMaximum(size) && constraint.getMaximum(size) < size) {
                        filter.add(ms -> !constraint.criterion.test(ms));
                    } else {
                        filter.add(constraint.criterion);
                    }
                    filter.add(constraint.criterion);
                    //Look for another units that meets all criteria
                    MechSummary replacement = table.generateUnit(ms -> filter.stream()
                            .allMatch(f -> f.test(ms)));
                    //If none is found, go to the next candidate. Otherwise, revise records of earlier matches and add to current list of matches
                    if (replacement == null) {
                        candidate++;
                    } else {
                        for (int j = 0; j < i; j++) {
                            allMatchingUnits.get(j).remove(unitList.get(candidate));
                            if (otherCriteria.get(j).criterion.test(replacement)) {
                                allMatchingUnits.get(j).add(replacement);
                            }
                        }
                        unitList.remove(candidate);
                        unitList.add(replacement);
                        matchingUnits.add(replacement);
                    }
                }
            }
            allMatchingUnits.add(matchingUnits);
        }
        
        return unitList;
    }
    
    /*
     * Rates how well the unit fulfills otherCriteria; used to judge which units
     * should be rerolled to meet requirements. Each constraint it matches gives a
     * +1 to the rating, unless the constraint is a maximum, in which case it gives
     * a -1 (since removing the unit may actually help, and won't hurt). 
     */
    private int needRating(MechSummary ms, int unitSize) {
        return otherCriteria.stream().filter(c -> c.criterion.test(ms))
                .mapToInt(c -> c.getMaximum(unitSize) < unitSize? -1 : 1)
                .sum();
    }
    
    public static void createFormationTypes() {
        allFormationTypes = new HashMap<>();
        createAssaultLance();
        createAnvilLance();
        createFastAssaultLance();
        createHunterLance();
        createBattleLance();
        createLightBattleLance();
        createMediumBattleLance();
        createHeavyBattleLance();
        createRifleLance();
        createBerserkerLance();
        createFireLance();
        createAntiAirLance();
        createArtilleryFireLance();
        createDirectFireLance();
        createFireSupportLance();
        createLightFireLance();
        createPursuitLance();
        createProbeLance();
        createSweepLance();
        createReconLance();
        createHeavyReconLance();
        createLightReconLance();
        createSecurityLance();
        createStrikerCavalryLance();
        createHammerLance();
        createHeavyStrikerCavalryLance();
        createHordeLance();
        createLightStrikerCavalryLance();
        createRangerLance();
        createUrbanLance();
        createAerospaceSuperioritySquadron();
        createEWSquadron();
        createFireSupportSquadron();
        createInterceptorSquadron();
        createStrikeSquadron();
        createTransportSquadron();
    }
    
    private static void createAssaultLance() {
        FormationType ft = new FormationType();
        ft.name = "Assault";
        ft.idealRole = UnitRole.JUGGERNAUT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getTotalArmor() >= 135;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> getDamageAtRange(ms, 7) >= 25));
        ft.otherCriteria.add(new CountConstraint(3,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        //FIXME: The actual requirement is one juggernaut or two snipers; there needs to be
        // a way to combine constraints with ||.
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.JUGGERNAUT, UnitRole.SNIPER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createAnvilLance() {
        FormationType ft = new FormationType();
        ft.name = "Anvil";
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getTotalArmor() >= 40;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ACWeapon
                            || eq instanceof LBXACWeapon
                            || eq instanceof UACWeapon
                            || eq instanceof SRMWeapon
                            || eq instanceof LRMWeapon)));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createFastAssaultLance() {
        FormationType ft = new FormationType();
        ft.name = "Fast Assault";
        ft.idealRole = UnitRole.JUGGERNAUT;
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getTotalArmor() >= 135
                && (ms.getWalkMp() >= 5 || ms.getJumpMp() > 0);
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> getDamageAtRange(ms, 7) >= 25));
        ft.otherCriteria.add(new CountConstraint(3,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        //FIXME: The actual requirement is one juggernaut or two snipers; there needs to be
        // a way to combine constraints with ||.
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.JUGGERNAUT, UnitRole.SNIPER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createHunterLance() {
        FormationType ft = new FormationType();
        ft.name = "Hunter";
        ft.idealRole = UnitRole.AMBUSHER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.JUGGERNAUT, UnitRole.AMBUSHER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
        
    private static void createBattleLance() {
        FormationType ft = new FormationType();
        ft.name = "Battle";
        ft.idealRole = UnitRole.BRAWLER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.BRAWLER, UnitRole.SNIPER, UnitRole.SKIRMISHER)
                    .contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }

    private static void createLightBattleLance() {
        FormationType ft = new FormationType();
        ft.name = "Light Battle";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWeightClass() == EntityWeightClass.WEIGHT_LIGHT));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createMediumBattleLance() {
        FormationType ft = new FormationType();
        ft.name = "Medium Battle";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() == EntityWeightClass.WEIGHT_MEDIUM));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createHeavyBattleLance() {
        FormationType ft = new FormationType();
        ft.name = "Heavy Battle";
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createRifleLance() {
        FormationType ft = new FormationType();
        ft.name = "Rifle";
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 4;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWeightClass() <= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ACWeapon
                            || eq instanceof LBXACWeapon
                            || eq instanceof UACWeapon))); //UAC includes RAC
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createBerserkerLance() {
        FormationType ft = new FormationType();
        ft.name = "Berserker";
        ft.idealRole = UnitRole.BRAWLER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.BRAWLER, UnitRole.SNIPER, UnitRole.SKIRMISHER)
                    .contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createFireLance() {
        FormationType ft = new FormationType();
        ft.name = "Fire";
        ft.idealRole = UnitRole.MISSILE_BOAT;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createAntiAirLance() {
        FormationType ft = new FormationType();
        ft.name = "Anti-Air";
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(2,
                // should indicate it has anti-aircraft targeting quirk without having to load all entities
                ms -> getMissionRoles(ms).contains(MissionRole.ANTI_AIRCRAFT) 
                || ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ACWeapon
                            || eq instanceof LBXACWeapon
                            || eq instanceof ArtilleryWeapon)));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createArtilleryFireLance() {
        FormationType ft = new FormationType();
        ft.name = "Artillery Fire";
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> ms.getEquipmentNames().stream().map(name -> EquipmentType.get(name))
                    .anyMatch(eq -> eq instanceof ArtilleryWeapon)));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createDirectFireLance() {
        FormationType ft = new FormationType();
        ft.name = "Direct Fire";
        ft.mainCriteria = ms -> getDamageAtRange(ms, 18) >= 10;
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createFireSupportLance() {
        FormationType ft = new FormationType();
        ft.name = "Fire Support";
        ft.otherCriteria.add(new CountConstraint(3, ms -> ms.getEquipmentNames().stream()
                .map(name -> EquipmentType.get(name))
                .filter(eq -> eq instanceof WeaponType && eq.hasModes())
                .anyMatch(eq -> {
                    for (Enumeration<EquipmentMode> e = eq.getModes(); e.hasMoreElements();) {
                        if (e.nextElement().toString().equals("Indirect")) {
                            return true;
                        }
                    }
                    return false;
                })));
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createLightFireLance() {
        FormationType ft = new FormationType();
        ft.name = "Light Fire";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createPursuitLance() {
        FormationType ft = new FormationType();
        ft.name = "Pursuit";
        ft.idealRole = UnitRole.SKIRMISHER;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWalkMp() >= 6));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> getDamageAtRange(ms, 15) >= 5));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createProbeLance() {
        FormationType ft = new FormationType();
        ft.name = "Probe";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.mainCriteria = ms -> getDamageAtRange(ms, 9) >= 10;
        ft.otherCriteria.add(new PercentConstraint(0.75,
                ms -> ms.getWalkMp() >= 6));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createSweepLance() {
        FormationType ft = new FormationType();
        ft.name = "Sweep";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5
                && getDamageAtRange(ms, 6) >= 10;
        allFormationTypes.put(ft.name, ft);
    }
    
    private static void createReconLance() {
        FormationType ft = new FormationType();
        ft.name = "Recon";
        ft.idealRole = UnitRole.SCOUT;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5;        
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.SCOUT, UnitRole.STRIKER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);
    }

    private static void createHeavyReconLance() {
        FormationType ft = new FormationType();
        ft.name = "Heavy Recon";
        ft.mainCriteria = ms -> ms.getWalkMp() >= 4;        
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> ms.getWalkMp() >= 5));
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> getUnitRole(ms).equals(UnitRole.SCOUT)));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createLightReconLance() {
        FormationType ft = new FormationType();
        ft.name = "Light Recon";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_LIGHT;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 6
                && getUnitRole(ms).equals(UnitRole.SCOUT);        
        allFormationTypes.put(ft.name, ft);        
    }
    
    private static void createSecurityLance() {
        FormationType ft = new FormationType();
        ft.name = "Security";
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> EnumSet.of(UnitRole.SCOUT, UnitRole.STRIKER).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> EnumSet.of(UnitRole.SNIPER, UnitRole.MISSILE_BOAT).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(0, 1,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_ASSAULT));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createStrikerCavalryLance() {
        FormationType ft = new FormationType();
        ft.name = "Striker/Cavalry";
        ft.idealRole = UnitRole.STRIKER;
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5 || ms.getJumpMp() >= 4;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> EnumSet.of(UnitRole.STRIKER, UnitRole.SKIRMISHER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createHammerLance() {
        FormationType ft = new FormationType();
        ft.name = "Hammer";
        ft.idealRole = UnitRole.STRIKER;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5;
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createHeavyStrikerCavalryLance() {
        FormationType ft = new FormationType();
        ft.name = "Heavy Striker/Cavalry";
        ft.minWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 4;
        ft.otherCriteria.add(new CountConstraint(3,
                ms -> ms.getWeightClass() >= EntityWeightClass.WEIGHT_HEAVY));
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.STRIKER, UnitRole.SKIRMISHER).contains(getUnitRole(ms))));
        ft.otherCriteria.add(new CountConstraint(1,
                ms -> getDamageAtRange(ms, 18) >= 5));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createHordeLance() {
        FormationType ft = new FormationType();
        ft.name = "Horde";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_LIGHT;
        ft.mainCriteria = ms -> getDamageAtRange(ms, 9) <= 10;
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createLightStrikerCavalryLance() {
        FormationType ft = new FormationType();
        ft.name = "Light Striker/Cavalry";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_MEDIUM;
        ft.mainCriteria = ms -> ms.getWalkMp() >= 5;
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> getDamageAtRange(ms, 18) >= 5));
        ft.otherCriteria.add(new CountConstraint(2,
                ms -> EnumSet.of(UnitRole.STRIKER, UnitRole.SKIRMISHER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createRangerLance() {
        FormationType ft = new FormationType();
        ft.name = "Ranger";
        ft.maxWeightClass = EntityWeightClass.WEIGHT_HEAVY;
        allFormationTypes.put(ft.name, ft);        
    }

    private static void createUrbanLance() {
        FormationType ft = new FormationType();
        ft.name = "Urban";
        ft.idealRole = UnitRole.AMBUSHER;
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getJumpMp() > 0
                    || ms.getUnitType().equals(UnitType.getTypeName(UnitType.INFANTRY))
                    || ms.getUnitType().equals(UnitType.getTypeName(UnitType.BATTLE_ARMOR))));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> ms.getWalkMp() <= 4));
        allFormationTypes.put(ft.name, ft);        
    }
    
    private static void createAerospaceSuperioritySquadron() {
        FormationType ft = new FormationType();
        ft.name = "Aerospace Superiority Squadron";
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> EnumSet.of(UnitRole.INTERCEPTOR, UnitRole.FAST_DOGFIGHTER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createEWSquadron() {
        FormationType ft = new FormationType();
        ft.name = "Electronic Warfare Squadron";
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> ms.getEquipmentNames().stream().map(en -> EquipmentType.get(en))
                .anyMatch(et -> et instanceof TAGWeapon ||  
                        (et instanceof MiscType &&
                            (((MiscType)et).hasFlag(MiscType.F_BAP)
                            || ((MiscType)et).hasFlag(MiscType.F_ECM))))));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createFireSupportSquadron() {
        FormationType ft = new FormationType();
        ft.name = "Fire Support Squadron";
        ft.mainCriteria = ms -> EnumSet.of(UnitRole.FIRE_SUPPORT,
                UnitRole.DOGFIGHTER).contains(getUnitRole(ms));
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> getUnitRole(ms).equals(UnitRole.FIRE_SUPPORT)));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createInterceptorSquadron() {
        FormationType ft = new FormationType();
        ft.name = "Interceptor Squadron";
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> getUnitRole(ms).equals(UnitRole.INTERCEPTOR)));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createStrikeSquadron() {
        FormationType ft = new FormationType();
        ft.name = "Strike Squadron";
        ft.otherCriteria.add(new PercentConstraint(0.51,
                ms -> EnumSet.of(UnitRole.ATTACK_FIGHTER,
                        UnitRole.DOGFIGHTER).contains(getUnitRole(ms))));
        allFormationTypes.put(ft.name, ft);                
    }

    private static void createTransportSquadron() {
        FormationType ft = new FormationType();
        ft.name = "Transport Squadron";
        ft.otherCriteria.add(new PercentConstraint(0.5,
                ms -> getUnitRole(ms).equals(UnitRole.TRANSPORT)));
        allFormationTypes.put(ft.name, ft);                
    }

    /**
     * base class for limitations on formation type 
     */
    public static abstract class Constraint {
        double minFraction = 0.0;
        double maxFraction = 1.0;
        
        Predicate<MechSummary> criterion;
        
        Constraint(Predicate<MechSummary> criterion) {
            this.criterion = criterion;
        }
        
        public abstract int getMinimum(int unitSize);
        public abstract int getMaximum(int unitSize);
        
        public boolean fits(MechSummary mRec) {
            return criterion.test(mRec);
        }
    }
    
    public static class CountConstraint extends Constraint {
        int minCount;
        int maxCount;
        
        public CountConstraint(int min, int max, Predicate<MechSummary> criterion) {
            super(criterion);
            minCount = min;
            maxCount = max;
        }
        
        public CountConstraint(int min, Predicate<MechSummary> criterion) {
            this(min, Integer.MAX_VALUE, criterion);
        }
        
        @Override
        public int getMinimum(int unitSize) {
            return minCount;
        }
        
        @Override
        public int getMaximum(int unitSize) {
            return maxCount;
        }
    }
    
    public static class PercentConstraint extends Constraint {
        double minPct;
        double maxPct;
        
        public PercentConstraint(double min, double max, Predicate<MechSummary> criterion) {
            super(criterion);
            minPct = min;
            maxPct = max;
        }
        
        public PercentConstraint(double min, Predicate<MechSummary> criterion) {
            this(min, 1.0, criterion);
        }
        
        @Override
        public int getMinimum(int unitSize) {
            return (int)(minPct * unitSize + 0.5);
        }
        
        @Override
        public int getMaximum(int unitSize) {
            return (int)(maxPct * unitSize + 0.5);
        }
    }
}