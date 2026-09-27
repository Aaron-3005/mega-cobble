{
  name: 'Dragonize',
  onModifyTypePriority: -1,
  onModifyType(move, pokemon) {
    const excluded = ['judgment', 'multiattack', 'naturalgift', 'revelationdance',
      'technoblast', 'terrainpulse', 'weatherball'];
    if (move.type === 'Normal' && (!excluded.includes(move.id) || (this.activeMove && this.activeMove.isMax)) &&
        !(move.isZ && move.category !== 'Status') && !(move.id === 'terablast' && pokemon.terastallized)) {
      move.type = 'Dragon';
      move.typeChangerBoosted = this.effect;
    }
  },
  onBasePowerPriority: 23,
  onBasePower(basePower, pokemon, target, move) {
    if (move.typeChangerBoosted === this.effect) return this.chainModify([4915, 4096]);
  }
}
