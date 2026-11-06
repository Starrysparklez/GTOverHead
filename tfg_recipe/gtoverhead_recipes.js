ServerEvents.recipes((event) => {
    /*
        SPOOLS
    */
    const tiers = [
        { spool: 'gtoverhead:tin_spool', wire: 'gtceu:tin_single_wire' },
        { spool: 'gtoverhead:copper_spool', wire: 'gtceu:copper_single_wire' },
        { spool: 'gtoverhead:steel_spool', wire: 'gtceu:steel_single_wire' },
        { spool: 'gtoverhead:aluminium_spool', wire: 'gtceu:aluminium_single_wire' },
        { spool: 'gtoverhead:platinum_spool', wire: 'gtceu:platinum_single_wire' },
    ];

    tiers.forEach((tier) => {
        event.shaped('2x ' + tier.spool, [
            'WWW',
            'WSW',
            'WWW',
        ], {
            W: tier.wire,
            S: 'gtoverhead:empty_spool'
        });
    });

    event.shaped('4x gtoverhead:empty_spool', [
        ' PL',
        'PLP',
        'LP ',
    ], {
        L: '#tfc:lumber',
        P: '#minecraft:planks'
    });

    /*
        TOOLS
    */

    event.shaped('gtoverhead:voltmeter', [
        'WGW',
        'CSC',
        'PFP',
    ], {
        W: 'gtceu:copper_single_wire',
        S: 'gtceu:fine_copper_wire',
        P: 'gtceu:polyethylene_plate',
        F: 'gtceu:copper_foil',
        C: '#gtceu:circuits/lv',
        G: 'gtceu:glass_plate'
    });

    // Zipline: GTOverHead (fork of Zipline by goldenxelenium)
    event.shaped('zipline:zipline', [
        'C C',
        'WPS',
        '   ',
    ], {
        C: "gtceu:mv_voltage_coil",
        P: "gtceu:lv_power_unit",
        W: '#forge:tools/wrenches',
        S: "#forge:tools/screwdrivers"
    });

    /*
        CONNECTORS
    */

    event.shaped('4x gtoverhead:structural_connector', [
        ' S ',
        'GGG',
        'PPP',
    ], {
        S: 'gtceu:long_steel_rod',
        G: 'firmalife:reinforced_glass',
        P: 'gtceu:steel_plate',
    });

    event.shaped('8x gtoverhead:insulator_garland', [
        ' S ',
        'GGG',
        ' S ',
    ], {
        S: 'gtceu:long_steel_rod',
        G: 'firmalife:reinforced_glass'
    });

    event.shaped('4x gtoverhead:lv_connector', [
        'R',
        'B',
        'P',
    ], {
        R: 'gtceu:tin_rod',
        B: 'tfc:ceramic/fire_brick',
        P: 'gtceu:steel_plate',
    });

    event.shaped('4x gtoverhead:mv_connector', [
        'R',
        'B',
        'P',
    ], {
        R: 'gtceu:copper_rod',
        B: 'tfc:ceramic/fire_brick',
        P: 'gtceu:steel_plate',
    });

    event.shaped('4x gtoverhead:hv_connector', [
        ' R ',
        'BBB',
        'PPP',
    ], {
        R: 'gtceu:long_steel_rod',
        B: 'tfc:ceramic/fire_brick',
        P: 'gtceu:steel_plate',
    });

    event.shaped('4x gtoverhead:ev_connector', [
        ' R ',
        'GGG',
        'PPP',
    ], {
        R: 'gtceu:long_aluminium_rod',
        G: 'firmalife:reinforced_glass',
        P: 'gtceu:steel_plate',
    });

    event.shaped('4x gtoverhead:iv_connector', [
        ' R ',
        'GGG',
        'PPP',
    ], {
        R: 'gtceu:long_platinum_rod',
        G: 'gtceu:borosilicate_glass_ingot',
        P: 'gtceu:steel_plate',
    });
});
