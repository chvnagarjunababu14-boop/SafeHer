#!/usr/bin/env python3
raw = """
able acid acre aged also aqua arch area army atom aunt auto away axis baby bake bald ball band bank
bare barn base bath beach bead beam bean bear beat been beer bell belt bend bent best bias bike bird
bite blew blob blot blow blue boat body boil bold bolt bomb bond bone book boom boot born boss both
bowl brunt buck bulb bulk bull bump burn bush busy buzz cage cake calf call calm came camp cane cape
card care cart case cash cast cave cell chat chef chew chin chip chop city clam clap claw clay clip
club clue coal coat code coil coin cold cola colt coma comb come cone cook cool cope cord cork corn
cost cozy crab crew crib crop crow cube cuff cull cult curb cure curl cute dale damp dare dark dart
dash data dawn days deal dean dear debt deck deep deer desk dial diet digs dime dine dirt dish disk
dock does doll dome done doom door dose dove down doze drag draw drew drip drop drum dual duck duct
duke dune dunk dust duty each earn ease east easy echo edge edit else emit envy epic even ever evil
exit face fact fade fail fair fake fall fame fang farm fast fate fawn fear feed feel feet fell felt
fern feud file fill film find fine fire firm fish fist five flag flat flaw fled flew flip flow foam
fold folk font food fool foot ford fork form fort foul four free frog from fuel full fuse gain gait
gale game gang gate gave gaze gear gene gift gill girl give glad glen glow glue goal goat gold golf
gone gong good gore grab gray grew grid grim grin grip grit grow gulf guru gust hail hair half hall
halt hand hang hard hare harm harp hash hate have hawk haze head heal heap hear heat heed heel heir
held helm help herb herd here hero hers hide high hike hill hind hint hire hold hole home honey hood
hoof hook hope horn hose host hour howl huge hulk hung hunt hurl hush hymn iced idea idle idol inch
into iris iron item jack jade jail jazz jeep jerk join joke jolt judo july jump june junk jury just
keen keep kelp kept keys kick kind king kite knee knew knob knot know lace lack lady laid lake lamb
lame lamp land lane lark last late lava lawn lead leaf leak lean leap left lend lens less levy liar
lime limp link lion list live load loaf loan lobe lock loft logo long look loop lord lore lose loss
lost loud love luck lump lung lure lush lute lyre made maid mail main make male mall malt mane many
maple march mare mark mars mash mask mass mate math maze meal mean meat meet melt menu mesh mice mild
mile milk mill mind mine mint miss mist mite moat mock mode mold mole monk mood moon moor mop more
moss most moth move much mule murk muse mush must mute myth nail name navy near neat neck need neon
nest news next nice nine node none noon norm nose note noun nuke null oak oars oath obey odor ogre
oil oily olive omit once only onto open oral orca otter ounce oven over pace pack page paid pail pain
pair pale palm pane park part pass past path pave pawn peak pear peat peck peel peer pent perk pest
pick pier pike pile pill pine pink pint pipe pity plan play plea plod plot plow plug plum plus poem
poet pole pond pony pool poor pope pork port pose post pour pray prep prey prop prow pull pulp pump
pure push putt quay queen quick quiet quilt quite quiz race rack raft rage raid rail rain rake ramp
rang rank rare rate rave read real reap rear redo reed reef reel rein rely rent rest rice rich ride
rift ring riot ripe rise risk road roam roar robe rock rode roll roof rook room root rope rose rosy
round ruby rude ruin rule rump rune rush rust safe sage said sail sake sale salt same sand sane sang
sank sash save says seal seam seat seed seek seem seen self sell send sent shed shin ship shop shot
show shut sick side sift sign silk silt sine sing sink site size skin skip slab slam slap sled slid
slim slip slit slow slug slum smug snag snap snip snow snug soak soap soar sock soft soil sold sole
solo some song soon soot sore sort soul soup sour sown span spark spat spin spit spot spur star stay
stem step stew stir stop stow stub stuck stud stun such suit sulk sung sunk sure surf swan swap sway
swim sync taco take tall tame tang tank tape tarp task taut teak team tear tech teen tell tend tent
term test text than that thaw them then they thin this thud thus tide tidy tied tier tiger tile tilt
time tint tiny tire toad toil told tone took tool tops torn tour town trap tray tree trek trim trio
trip trot true tuba tube tuck tuna turf turn tusk twin type ugly undo unit unto upon urge used user
vain vane vase vast veal veil vein vent verb very vest veto vice view vine visa void volt vote wade
wage wait wake walk wall wand want ward warm warn warp wart wash wasp wave wavy weak wear weed week
weep well went were west whale what wheat when whip whir wick wide wife wild will wilt wind wine wing
wink wipe wire wise wish with wolf wood wool word wore work worm worn wrap wren yard yarn yawn year
yell yoga yoke yolk your zeal zero zone zoom amber angel anvil apricot arcade armor arrow autumn
avocado bamboo banner barley beacon berry birch blossom breeze bronze canyon cedar cherry citrus cliff
comet copper coral cosmos cotton crater crimson crystal daisy delta desert diamond dragon eagle ember
emerald forest frost galaxy garden ginger glacier granite harbor hazel indigo ivory jasmine jungle
kettle lemon linen lotus marble meadow mercury mirror nectar nickel ocean onion orchid oyster peach
pearl pepper petal pioneer planet plaza plume prairie quartz rabbit raven river rocket sable sapphire
scarlet shadow silver spruce stone storm sunset tablet thunder timber titanium torus tulip turbo
turret umber valley velvet violet walnut willow winter zebra
"""
seen = set()
out = []
for w in raw.split():
    w = w.lower()
    if w.isalpha() and 3 <= len(w) <= 10 and w not in seen:
        seen.add(w)
        out.append(w)

print("count", len(out))
path = r"c:\Users\chvna\OneDrive\Desktop\Projects\SafeHer\app\src\main\java\com\safeher\app\data\remoteguardian\InvitePhraseWords.kt"
lines = [
    "package com.safeher.app.data.remoteguardian",
    "",
    "/**",
    " * Curated word list for human-readable invitation phrases.",
    " * Not a dictionary attack target by itself — combined with SecureRandom selection,",
    " * numeric suffix, short TTL, and one-time claim.",
    " */",
    "internal object InvitePhraseWords {",
    "    val WORDS: List<String> = listOf(",
]
for i in range(0, len(out), 10):
    chunk = ", ".join(f'"{x}"' for x in out[i : i + 10])
    suffix = "," if i + 10 < len(out) else ""
    lines.append(f"        {chunk}{suffix}")
lines.append("    )")
lines.append("}")
lines.append("")
with open(path, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("wrote", path)
