import { describe, expect, it } from 'vitest'
import { FORMATIONS, FORMATION_SLOTS, remapSlots } from './formations'

const FULL = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]

describe('remapSlots (issue #109)', () => {
  it('4-4-2 completo → 4-3-3 sai um meia e só o terceiro slot de atacante fica vazio', () => {
    expect(remapSlots(FULL, '4-4-2', '4-3-3')).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 10, 11, null])
  })

  it('preenche os slots novos da posição só com quem já era dela', () => {
    expect(remapSlots(FULL, '4-4-2', '3-5-2')).toEqual([1, 2, 3, 4, 6, 7, 8, 9, null, 10, 11])
    expect(remapSlots(FULL, '4-4-2', '5-3-2')).toEqual([1, 2, 3, 4, 5, null, 6, 7, 8, 10, 11])
  })

  it('slots vazios não ocupam vaga: os jogadores restantes sobem em ordem', () => {
    const withGaps = [1, null, 3, 4, 5, null, 7, 8, 9, null, 11]
    expect(remapSlots(withGaps, '4-4-2', '5-3-2')).toEqual([1, 3, 4, 5, null, null, 7, 8, 9, 11, null])
  })

  it('escalação vazia continua vazia', () => {
    expect(remapSlots(Array(11).fill(null), '4-4-2', '4-3-3')).toEqual(Array(11).fill(null))
  })

  it('em qualquer troca: 11 slots, sem duplicados e cada um na posição de origem', () => {
    for (const from of FORMATIONS) {
      for (const to of FORMATIONS) {
        const result = remapSlots(FULL, from, to)
        expect(result).toHaveLength(11)

        const kept = result.filter((id): id is number => id !== null)
        expect(new Set(kept).size).toBe(kept.length)
        result.forEach((id, i) => {
          if (id !== null) expect(FORMATION_SLOTS[from][id - 1]).toBe(FORMATION_SLOTS[to][i])
        })
      }
    }
  })
})
