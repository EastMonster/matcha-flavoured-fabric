package monster.east.matchaff.mechanic;

/** One player's Aura windup, in server ticks. */
record AuraCharge(int triggerTick, int duration) {
	static AuraCharge startOrUpdate(AuraCharge pending, int now, int duration) {
		return new AuraCharge(pending == null ? now + 48 : pending.triggerTick, duration);
	}

	boolean ready(int now) {
		return now >= triggerTick;
	}
}
