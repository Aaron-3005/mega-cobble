{
  name: 'Fire Mane',
  onModifyAtkPriority: 5,
  onModifyAtk(attack, attacker, defender, move) {
    if (move.type === 'Fire') return this.chainModify(1.5);
  },
  onModifySpAPriority: 5,
  onModifySpA(attack, attacker, defender, move) {
    if (move.type === 'Fire') return this.chainModify(1.5);
  }
}
