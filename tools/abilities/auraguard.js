{
  name: 'Aura Guard',
  flags: { breakable: 1 },
  onSourceModifyDamage(damage, source, target, move) {
    if (move.flags.contact) return this.chainModify(0.5);
  }
}
