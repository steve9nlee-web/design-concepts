package com.fittrainer.app.data

enum class AgeCategory(val label: String, val range: String) {
    TEEN("Teen", "13-17"),
    YOUNG_ADULT("Young Adult", "18-35"),
    MIDDLE_AGE("Middle Age", "36-50"),
    SENIOR("Senior", "51-64"),
    ELDER("Elder", "65+");

    companion object {
        fun fromName(n: String?): AgeCategory =
            entries.firstOrNull { it.name == n } ?: YOUNG_ADULT
    }
}

enum class Area(val label: String, val emoji: String, val description: String) {
    LEGS("Legs", "🦵", "Quads, hamstrings, glutes and calves"),
    ARMS("Arms", "💪", "Biceps, triceps, forearms and grip"),
    CHEST("Chest", "🏋", "Pectorals and pushing strength"),
    BACK("Back", "🧍", "Lats, traps, lower back and posture"),
    SHOULDERS("Shoulders", "🤾", "Deltoids and rotator cuff"),
    CORE("Core / Body", "🦾", "Abs, obliques and full-body stability"),
    STAMINA("Stamina / Cardio", "🏃", "Heart, lungs and endurance"),
    FLEXIBILITY("Flexibility", "🧘", "Mobility, stretching and balance")
}

enum class ExType { REPS, TIME }

/**
 * One exercise. Base numbers describe the Young Adult (18-35) prescription;
 * every other age category is scaled from it via its multiplier and gets
 * an age-specific safety/method note.
 */
data class Exercise(
    val id: String,
    val area: Area,
    val name: String,
    val type: ExType,
    val method: List<String>,      // step-by-step how to perform
    val baseSets: Int,
    val baseAmount: Int,           // reps (REPS) or seconds (TIME) per set
    val restSeconds: Int,
    val goal4Weeks: String         // what to achieve in 4 weeks (young-adult base)
)

data class Prescription(
    val sets: Int,
    val amount: Int,               // reps or seconds per set
    val restSeconds: Int,
    val ageNote: String,
    val goalText: String
)

object ExerciseDb {

    private fun mult(cat: AgeCategory): Double = when (cat) {
        AgeCategory.TEEN -> 0.75
        AgeCategory.YOUNG_ADULT -> 1.0
        AgeCategory.MIDDLE_AGE -> 0.85
        AgeCategory.SENIOR -> 0.65
        AgeCategory.ELDER -> 0.45
    }

    private fun ageNote(cat: AgeCategory): String = when (cat) {
        AgeCategory.TEEN ->
            "Focus on learning perfect technique with body weight before adding any load. " +
            "Avoid maximal lifts while still growing. 60+ min of activity daily is recommended (WHO)."
        AgeCategory.YOUNG_ADULT ->
            "Peak training window: progressive overload is safe and effective. " +
            "Aim for 150-300 min moderate cardio + 2 strength days per week (WHO)."
        AgeCategory.MIDDLE_AGE ->
            "Warm up 5-10 min before every session and prioritise joint-friendly form. " +
            "Recovery takes longer now - keep at least 48h between hard sessions for the same muscles."
        AgeCategory.SENIOR ->
            "Use controlled tempo and lighter loads with more repetitions. " +
            "Strength training 2-3x/week helps preserve muscle and bone density."
        AgeCategory.ELDER ->
            "Move within a pain-free range and keep a chair or wall nearby for balance support. " +
            "Balance and fall-prevention work 3x/week is recommended (WHO 65+). Consult a doctor before starting."
    }

    fun prescriptionFor(e: Exercise, cat: AgeCategory): Prescription {
        val m = mult(cat)
        val sets = when (cat) {
            AgeCategory.ELDER -> maxOf(1, e.baseSets - 1)
            AgeCategory.SENIOR -> maxOf(2, e.baseSets - 1)
            else -> e.baseSets
        }
        val amount = maxOf(if (e.type == ExType.TIME) 10 else 4, Math.round(e.baseAmount * m).toInt())
        val rest = when (cat) {
            AgeCategory.SENIOR, AgeCategory.ELDER -> e.restSeconds + 30
            AgeCategory.MIDDLE_AGE -> e.restSeconds + 15
            else -> e.restSeconds
        }
        val unit = if (e.type == ExType.TIME) "sec" else "reps"
        val goal = "4-week target for ${cat.label} (${cat.range}): build up to " +
                "${sets} sets of ${Math.round(amount * 1.4).toInt()} $unit. ${e.goal4Weeks}"
        return Prescription(sets, amount, rest, ageNote(cat), goal)
    }

    val exercises: List<Exercise> = listOf(
        // ---------------- LEGS ----------------
        Exercise("squat", Area.LEGS, "Bodyweight Squat", ExType.REPS,
            listOf(
                "Stand with feet shoulder-width apart, toes slightly out.",
                "Brace your core and keep your chest up.",
                "Bend knees and push hips back as if sitting on a chair.",
                "Lower until thighs are parallel to the floor (or as deep as comfortable).",
                "Drive through your heels to stand back up."
            ), 3, 15, 60,
            "Then progress to goblet squats or jump squats."),
        Exercise("lunge", Area.LEGS, "Forward Lunge", ExType.REPS,
            listOf(
                "Stand tall, hands on hips.",
                "Step forward with one leg about one stride length.",
                "Lower until both knees are bent ~90°; back knee hovers above floor.",
                "Front knee stays over the ankle, not past the toes.",
                "Push off the front foot to return. Alternate legs."
            ), 3, 12, 60,
            "Then add walking lunges or hold light weights."),
        Exercise("calf", Area.LEGS, "Calf Raise", ExType.REPS,
            listOf(
                "Stand with the balls of your feet on a step or flat floor.",
                "Hold a wall or rail lightly for balance.",
                "Rise up onto your toes as high as possible.",
                "Pause 1 second at the top, feel the calf squeeze.",
                "Lower slowly over 2-3 seconds."
            ), 3, 20, 45,
            "Then progress to single-leg calf raises."),
        Exercise("wallsit", Area.LEGS, "Wall Sit", ExType.TIME,
            listOf(
                "Lean your back flat against a wall.",
                "Walk feet forward and slide down until knees are at 90°.",
                "Keep knees above ankles and back pressed to the wall.",
                "Hold the position, breathing steadily.",
                "Slide back up the wall to finish."
            ), 3, 40, 60,
            "Then hold a single continuous 90-second wall sit."),
        Exercise("glutebridge", Area.LEGS, "Glute Bridge", ExType.REPS,
            listOf(
                "Lie on your back, knees bent, feet flat and hip-width apart.",
                "Arms by your sides, palms down.",
                "Squeeze glutes and lift hips until body forms a straight line from knees to shoulders.",
                "Hold 2 seconds at the top.",
                "Lower with control."
            ), 3, 15, 45,
            "Then progress to single-leg bridges."),
        Exercise("steplegs", Area.LEGS, "Step-Ups", ExType.REPS,
            listOf(
                "Stand facing a sturdy step, bench or stair.",
                "Step up with the right foot, driving through the heel.",
                "Bring the left foot up to stand fully on the step.",
                "Step back down with control.",
                "Alternate the leading leg each rep."
            ), 3, 12, 60,
            "Then use a higher step or add light dumbbells."),

        // ---------------- ARMS ----------------
        Exercise("pushup_narrow", Area.ARMS, "Close-Grip Push-Up (Triceps)", ExType.REPS,
            listOf(
                "Start in a plank with hands directly under shoulders, close together.",
                "Keep elbows tucked to your sides.",
                "Lower your chest towards your hands.",
                "Press back up until arms are straight.",
                "Drop to knees to make it easier if needed."
            ), 3, 10, 60,
            "Then progress to full diamond push-ups."),
        Exercise("chairdip", Area.ARMS, "Chair / Bench Dip", ExType.REPS,
            listOf(
                "Sit on the edge of a sturdy chair, hands gripping the edge beside your hips.",
                "Slide your hips off, legs extended or bent (easier).",
                "Lower your body by bending elbows to ~90°.",
                "Keep elbows pointing straight back, shoulders down.",
                "Press back up to straight arms."
            ), 3, 10, 60,
            "Then straighten legs fully or elevate feet."),
        Exercise("curl", Area.ARMS, "Biceps Curl (bottle/band/dumbbell)", ExType.REPS,
            listOf(
                "Hold a weight in each hand (water bottles work), arms at sides, palms forward.",
                "Pin elbows to your ribs.",
                "Curl the weights up to shoulder height.",
                "Squeeze the biceps at the top.",
                "Lower slowly over 2-3 seconds - no swinging."
            ), 3, 12, 45,
            "Then increase the weight or slow the lowering phase to 4 seconds."),
        Exercise("armplankup", Area.ARMS, "Plank Up-Down", ExType.REPS,
            listOf(
                "Start in a forearm plank.",
                "Place your right hand down and press up to a straight-arm plank.",
                "Follow with the left hand.",
                "Lower back to forearms one arm at a time.",
                "Keep hips as still as possible. One full cycle = 1 rep."
            ), 3, 8, 60,
            "Then perform continuous cycles for 45 seconds without stopping."),
        Exercise("wristcurl", Area.ARMS, "Wrist & Grip Curl", ExType.REPS,
            listOf(
                "Sit with forearms resting on your thighs, palms up, holding light weights.",
                "Let the weights roll to your fingertips.",
                "Curl them back into your palms, then flex the wrists upward.",
                "Lower slowly.",
                "Strengthens grip and forearms."
            ), 3, 15, 30,
            "Then add a towel-wring hold for 30 seconds after each set."),

        // ---------------- CHEST ----------------
        Exercise("pushup", Area.CHEST, "Push-Up", ExType.REPS,
            listOf(
                "Hands slightly wider than shoulders, body in a straight line.",
                "Brace core and squeeze glutes.",
                "Lower chest to just above the floor, elbows ~45° from body.",
                "Press back up to full arm extension.",
                "Do it on knees or against a wall to make it easier."
            ), 3, 12, 60,
            "Then progress to decline push-ups (feet elevated)."),
        Exercise("inclinepush", Area.CHEST, "Incline Push-Up", ExType.REPS,
            listOf(
                "Place hands on a table, bench or wall - the higher, the easier.",
                "Body forms a straight line from head to heels.",
                "Lower chest to the edge.",
                "Press back up under control.",
                "Great starting point for beginners and older trainees."
            ), 3, 12, 45,
            "Then move to a lower surface each week until floor level."),
        Exercise("chestfly", Area.CHEST, "Floor Chest Fly (bottles/dumbbells)", ExType.REPS,
            listOf(
                "Lie on your back, knees bent, a light weight in each hand above your chest.",
                "Keep a slight bend in the elbows.",
                "Open arms out wide until the upper arms touch the floor.",
                "Squeeze the chest to bring the weights back together.",
                "Move slowly in both directions."
            ), 3, 12, 45,
            "Then increase weight slightly while keeping a 3-second opening phase."),
        Exercise("chestpass", Area.CHEST, "Wall Chest Pass (ball/pillow)", ExType.REPS,
            listOf(
                "Stand ~1.5 m from a wall holding a ball or cushion at chest height.",
                "Push it explosively towards the wall (or just extend arms fast with a pillow).",
                "Catch and absorb with bent elbows.",
                "Keep core braced throughout.",
                "Builds pushing power safely."
            ), 3, 10, 60,
            "Then increase distance from the wall or use a heavier ball."),

        // ---------------- BACK ----------------
        Exercise("superman", Area.BACK, "Superman Hold", ExType.TIME,
            listOf(
                "Lie face down, arms extended overhead.",
                "Lift arms, chest and legs off the floor together.",
                "Look down to keep the neck neutral.",
                "Hold, breathing steadily.",
                "Lower with control."
            ), 3, 20, 45,
            "Then add gentle arm/leg flutters during the hold."),
        Exercise("row", Area.BACK, "Bent-Over Row (bottles/band/dumbbells)", ExType.REPS,
            listOf(
                "Hold weights, hinge at hips to ~45°, back flat, knees soft.",
                "Let arms hang straight down.",
                "Pull elbows back and up, squeezing shoulder blades together.",
                "Pause 1 second at the top.",
                "Lower slowly. Never round the lower back."
            ), 3, 12, 60,
            "Then increase weight or move to single-arm rows."),
        Exercise("reversesnow", Area.BACK, "Reverse Snow Angel", ExType.REPS,
            listOf(
                "Lie face down, arms by your sides, palms down.",
                "Lift chest slightly and hover arms off the floor.",
                "Sweep arms overhead in a wide arc like a snow angel.",
                "Sweep back to your hips.",
                "Keep arms off the ground the whole time. One full sweep = 1 rep."
            ), 3, 10, 45,
            "Then hold light weights (0.5-1 kg) during the sweep."),
        Exercise("birddog", Area.BACK, "Bird Dog", ExType.REPS,
            listOf(
                "Start on all fours, hands under shoulders, knees under hips.",
                "Extend right arm forward and left leg back until level with your torso.",
                "Hold 2 seconds, keeping hips square.",
                "Return and switch sides.",
                "One side + the other = 1 rep. Excellent for lower-back health."
            ), 3, 10, 45,
            "Then hold each extension for 5 seconds."),

        // ---------------- SHOULDERS ----------------
        Exercise("pikepush", Area.SHOULDERS, "Pike Push-Up", ExType.REPS,
            listOf(
                "From push-up position, walk feet in and lift hips high (inverted V).",
                "Bend elbows to lower the top of your head towards the floor.",
                "Press back up to straight arms.",
                "Keep legs as straight as possible.",
                "Elevate feet to make it harder."
            ), 3, 8, 60,
            "Then elevate the feet on a step to increase the load."),
        Exercise("lateralraise", Area.SHOULDERS, "Lateral Raise (bottles/band)", ExType.REPS,
            listOf(
                "Stand tall, light weight in each hand at your sides.",
                "With a slight elbow bend, raise arms out to shoulder height.",
                "Pause briefly - do not shrug.",
                "Lower slowly over 3 seconds.",
                "Light weight, strict form."
            ), 3, 12, 45,
            "Then add a 2-second hold at the top of every rep."),
        Exercise("shoulderpress", Area.SHOULDERS, "Overhead Press (bottles/dumbbells)", ExType.REPS,
            listOf(
                "Stand or sit tall, weights at shoulder height, palms forward.",
                "Brace core - do not arch the lower back.",
                "Press the weights straight up until arms are extended.",
                "Lower back to shoulders with control.",
                "Keep wrists stacked over elbows."
            ), 3, 10, 60,
            "Then increase weight gradually (5-10% per week)."),
        Exercise("armcircle", Area.SHOULDERS, "Arm Circles", ExType.TIME,
            listOf(
                "Stand with arms extended out to the sides at shoulder height.",
                "Make small controlled circles forward.",
                "Halfway through the time, switch to backward circles.",
                "Keep shoulders down away from ears.",
                "Great warm-up and endurance builder for the delts."
            ), 3, 30, 30,
            "Then perform 60 seconds continuously with larger circles."),

        // ---------------- CORE / BODY ----------------
        Exercise("plank", Area.CORE, "Plank", ExType.TIME,
            listOf(
                "Forearms on the floor, elbows under shoulders.",
                "Body in one straight line from head to heels.",
                "Squeeze glutes and brace abs - don't let hips sag or pike.",
                "Breathe steadily while holding.",
                "Drop to knees if form breaks."
            ), 3, 30, 45,
            "Then reach a single 2-minute continuous plank."),
        Exercise("crunch", Area.CORE, "Crunch", ExType.REPS,
            listOf(
                "Lie on your back, knees bent, hands lightly behind ears.",
                "Tuck chin slightly.",
                "Curl shoulders off the floor using your abs.",
                "Pause at the top, exhale.",
                "Lower slowly - don't pull on your neck."
            ), 3, 15, 45,
            "Then progress to bicycle crunches."),
        Exercise("sideplank", Area.CORE, "Side Plank", ExType.TIME,
            listOf(
                "Lie on your side, forearm under shoulder.",
                "Stack or stagger your feet.",
                "Lift hips so body forms a straight line.",
                "Hold, then switch sides (timer counts one side).",
                "Drop the bottom knee to make it easier."
            ), 3, 20, 45,
            "Then add hip dips during the hold."),
        Exercise("mountain", Area.CORE, "Mountain Climbers", ExType.TIME,
            listOf(
                "Start in a straight-arm plank.",
                "Drive one knee towards your chest.",
                "Switch legs quickly, like running in place horizontally.",
                "Keep hips level and core tight.",
                "Slow the pace down to reduce intensity."
            ), 3, 30, 60,
            "Then increase pace while keeping hips perfectly level."),
        Exercise("deadbug", Area.CORE, "Dead Bug", ExType.REPS,
            listOf(
                "Lie on your back, arms pointing at the ceiling, knees bent 90° over hips.",
                "Press lower back into the floor.",
                "Slowly extend the right arm overhead and left leg forward.",
                "Return and switch sides.",
                "Both sides = 1 rep. Stop if the lower back arches."
            ), 3, 10, 45,
            "Then hold each extension for 3 breaths."),

        // ---------------- STAMINA / CARDIO ----------------
        Exercise("jumping", Area.STAMINA, "Jumping Jacks", ExType.TIME,
            listOf(
                "Stand with feet together, arms at sides.",
                "Jump feet out while raising arms overhead.",
                "Jump back to the start position.",
                "Keep a steady rhythm and soft knees.",
                "Step side-to-side instead of jumping for low impact."
            ), 3, 45, 45,
            "Then complete 3 minutes continuously without stopping."),
        Exercise("highknees", Area.STAMINA, "High Knees", ExType.TIME,
            listOf(
                "Run in place, driving knees up to hip height.",
                "Pump your arms as if sprinting.",
                "Stay on the balls of your feet.",
                "Keep your chest up.",
                "March in place for a low-impact version."
            ), 3, 30, 60,
            "Then increase to 60-second rounds at sprint pace."),
        Exercise("burpee", Area.STAMINA, "Burpee", ExType.REPS,
            listOf(
                "From standing, squat and place hands on the floor.",
                "Jump (or step) feet back to a plank.",
                "Optional: one push-up.",
                "Jump (or step) feet back in.",
                "Stand and jump with arms overhead."
            ), 3, 8, 90,
            "Then perform 10 unbroken burpees per set with the push-up included."),
        Exercise("brisk", Area.STAMINA, "Brisk Walk / Jog Interval", ExType.TIME,
            listOf(
                "Warm up walking easy for 2 minutes.",
                "Alternate 1 minute brisk pace with 1 minute easy pace.",
                "Brisk = you can talk but not sing.",
                "Swing arms naturally, land softly.",
                "The timer here counts one brisk interval."
            ), 5, 60, 60,
            "Then extend brisk intervals to 3 minutes each."),
        Exercise("stepcardio", Area.STAMINA, "Stair Climb", ExType.TIME,
            listOf(
                "Find a staircase or a single sturdy step.",
                "Climb up and down at a steady rhythm.",
                "Use the handrail for safety if needed.",
                "Drive through the whole foot.",
                "Excellent low-equipment cardio."
            ), 3, 60, 60,
            "Then climb continuously for 5 minutes without a break."),
        Exercise("shadowbox", Area.STAMINA, "Shadow Boxing", ExType.TIME,
            listOf(
                "Stand in a boxing stance, fists up by your chin.",
                "Throw light jabs and crosses at the air.",
                "Keep moving your feet - small bounces or steps.",
                "Exhale sharply with each punch.",
                "Mix in ducks and slips to raise intensity."
            ), 3, 60, 60,
            "Then add knee strikes and 2-minute rounds."),

        // ---------------- FLEXIBILITY ----------------
        Exercise("hamstretch", Area.FLEXIBILITY, "Standing Hamstring Stretch", ExType.TIME,
            listOf(
                "Place one heel on a low step, leg straight.",
                "Keep your back flat and chest tall.",
                "Hinge forward from the hips until you feel a gentle stretch.",
                "Hold and breathe - no bouncing.",
                "The timer counts one side; repeat on the other."
            ), 2, 30, 15,
            "Then reach further while keeping the spine neutral."),
        Exercise("quadstretch", Area.FLEXIBILITY, "Standing Quad Stretch", ExType.TIME,
            listOf(
                "Hold a wall or chair with one hand.",
                "Bend one knee and grab that ankle behind you.",
                "Pull the heel gently towards your glutes.",
                "Keep knees together and stand tall.",
                "Hold, then switch legs."
            ), 2, 30, 15,
            "Then perform without holding support to add balance work."),
        Exercise("catcow", Area.FLEXIBILITY, "Cat-Cow Stretch", ExType.TIME,
            listOf(
                "Start on all fours.",
                "Inhale: drop the belly, lift chest and tailbone (Cow).",
                "Exhale: round the spine, tuck chin and pelvis (Cat).",
                "Flow slowly between the two with your breath.",
                "Wonderful for spine mobility."
            ), 2, 45, 15,
            "Then slow each cycle to 6 seconds for deeper mobility."),
        Exercise("childpose", Area.FLEXIBILITY, "Child's Pose", ExType.TIME,
            listOf(
                "Kneel and sit back on your heels.",
                "Fold forward, arms extended on the floor.",
                "Rest your forehead down and relax the shoulders.",
                "Breathe deeply into your back.",
                "A recovery stretch for back, hips and shoulders."
            ), 2, 45, 15,
            "Then walk hands to each side to open the lats."),
        Exercise("balance", Area.FLEXIBILITY, "Single-Leg Balance", ExType.TIME,
            listOf(
                "Stand tall near a wall or chair for safety.",
                "Lift one foot slightly off the floor.",
                "Fix your eyes on a point ahead.",
                "Hold steady, then switch legs.",
                "Key fall-prevention exercise for older adults."
            ), 2, 20, 15,
            "Then try holding with eyes closed (hand near support)."),
        Exercise("neckshoulder", Area.FLEXIBILITY, "Neck & Shoulder Rolls", ExType.TIME,
            listOf(
                "Sit or stand tall.",
                "Roll shoulders backwards slowly 5 times, then forwards.",
                "Tilt your ear towards one shoulder, hold 5 seconds, switch.",
                "Turn your head gently left and right.",
                "Keep every movement slow and pain-free."
            ), 2, 30, 15,
            "Then add gentle resistance with your hand for 3-second holds.")
    )

    fun byArea(area: Area): List<Exercise> = exercises.filter { it.area == area }
    fun byId(id: String): Exercise? = exercises.firstOrNull { it.id == id }
}
