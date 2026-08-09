package com.feanor08.onering.sample.ui

/** One catalog entry on the showcase home screen. Selecting one opens its dedicated demo screen. */
enum class Demo(val title: String, val summary: String) {
    Basic("Basic", "A flat ring of actions -- press, scrub, release-to-commit."),
    Nested("Nested", "One level of submenu navigation, with the parent-gap reserved."),
    Weighted(
        "Asymmetrical / Weighted",
        "Sectors sized by relative weight -- 2/1/1 gives 50%/25%/25% -- with a live weight editor.",
    ),
    NestedGrowth(
        "Nested Growth",
        "The submenu growing outer-edge inward, on a weighted parent with weighted children and a seek.",
    ),
    Seek("Seek", "Radial sliders on the main ring and inside a submenu, with arbitrary ranges and steps."),
    ItemCounts("Item Counts", "The geometry engine at 2 through 12 items -- proving it isn't hardcoded to six."),
    Geometry("Geometry", "Live sliders over every radius, angle, and spacing knob."),
    Styling("Styling", "Color presets and individual color overrides, all through the public style API."),
    Motion("Motion", "Animation-speed presets and the seek intro hint's overshoot."),
    Dynamic("Dynamic", "Add, remove, shuffle, and disable items at runtime -- no Activity restart."),
    Automotive("Automotive", "A vehicle-HMI preset: larger targets, a climate submenu, a radial seek."),
    GameWheel("Game Wheel", "An 8-item weapon-select wheel -- one-ring isn't automotive-specific."),
    SmartHome("Smart Home", "Lights / fan / AC / scenes, with a brightness seek nested under Lights."),
    Media("Media", "Transport controls with a scrub seek and a dynamically toggling play/pause icon."),
    StressTest("Stress Test", "The stutter-regression proof, plus a rapid open/close/scrub torture test."),
    Accessibility("Accessibility", "What TalkBack sees today, and what still needs a real drag gesture."),
}
