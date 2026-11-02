ServerEvents.tags('item', event => {
    event.add('kubejs:rubber_plates', [
        'gtceu:rubber_plate',
        'gtceu:silicone_rubber_plate',
        'gtceu:styrene_butadiene_rubber_plate'
    ])
});

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

    event.shaped('gtoverhead:empty_spool', [
        ' PL',
        'PLP',
        'LP ',
    ], {
        L: '#tfc:lumber',
        P: '#minecraft:planks'
    });

    /*
        CONNECTORS
    */

    event.shaped('gtoverhead:structural_connector', [
        ' S ',
        'GGG',
        'RRR',
    ], {
        S: 'gtceu:long_steel_rod',
        G: 'firmalife:reinforced_glass',
        R: '#kubejs:rubber_plates',
    });

    event.shaped('gtoverhead:lv_connector', [
        'R',
        'B',
        'P',
    ], {
        R: 'gtceu:tin_rod',
        B: 'tfc:ceramic/fire_brick',
        P: '#kubejs:rubber_plates',
    });

    event.shaped('gtoverhead:mv_connector', [
        'R',
        'B',
        'P',
    ], {
        R: 'gtceu:copper_rod',
        B: 'tfc:ceramic/fire_brick',
        P: '#kubejs:rubber_plates',
    });

    event.shaped('gtoverhead:hv_connector', [
        ' R ',
        'BBB',
        'PPP',
    ], {
        R: 'gtceu:long_steel_rod',
        B: 'tfc:ceramic/fire_brick',
        P: '#kubejs:rubber_plates',
    });

    event.shaped('gtoverhead:ev_connector', [
        ' R ',
        'GGG',
        'PPP',
    ], {
        R: 'gtceu:long_aluminium_rod',
        G: 'firmalive:reinforced_glass',
        P: '#kubejs:rubber_plates',
    });

    event.shaped('gtoverhead:iv_connector', [
        ' R ',
        'GGG',
        'PPP',
    ], {
        R: 'gtceu:long_platinum_rod',
        G: 'gtceu:borosilicate_glass_ingot',
        P: '#kubejs:rubber_plates',
    });
});
