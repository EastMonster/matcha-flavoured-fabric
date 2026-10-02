package monster.east.matchaff.mechanic;

/** One player's Aura windup, in server ticks. */
record AuraCharge(int triggerTick, int duration) {
	static AuraCharge startOrUpdate(AuraCharge pending, int now, int duration) {
		return new AuraCharge(pending == null ? now + 48 : pending.triggerTick, duration);
	}

	boolean ready(int now) {
		return now >= triggerTick;
	}

	/** Standalone check: java AuraCharge.java (no Minecraft or Gradle required). */
	public static void main(String[] args) {
		AuraCharge first = startOrUpdate(null, 100, 60);
		AuraCharge updated = startOrUpdate(first, 116, 1200);
		if (first.triggerTick != 148 || updated.triggerTick != 148 || updated.duration != 1200
				|| updated.ready(147) || !updated.ready(148)
				|| startOrUpdate(updated, 120, 60).duration != 60
				|| startOrUpdate(null, 149, 600).triggerTick != 197) {
			throw new AssertionError("Aura windup/duration mismatch");
		}
		System.out.println("Aura charge checks passed");
	}
}
