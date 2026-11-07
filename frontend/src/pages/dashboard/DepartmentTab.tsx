import { useSetTrainingFocus } from '../../api/queries/useSetTrainingFocus'
import { useTreatSquad } from '../../api/queries/useTreatSquad'
import { MedicalDepartmentPanel } from '../../components/MedicalDepartmentPanel'
import { TrainingPanel } from '../../components/TrainingPanel'
import { useDashboardClub } from './tabs'

/** Aba "Departamento": treino da semana e departamento médico. */
export function DepartmentTab() {
  const club = useDashboardClub()
  const setFocus = useSetTrainingFocus(club.id)
  const treat = useTreatSquad(club.id)

  return (
    <div className="space-y-6">
      <TrainingPanel
        // Otimismo local só para o feedback do clique: a fonte de verdade continua
        // sendo `club.trainingFocus`, que o mutation atualiza no cache.
        selected={setFocus.isPending ? setFocus.variables : club.trainingFocus}
        onSelect={(focus) => setFocus.mutate(focus)}
        pending={setFocus.isPending}
        error={setFocus.error}
      />
      <MedicalDepartmentPanel
        squad={club.squad}
        cash={club.cash}
        onTreat={() => treat.mutate()}
        pending={treat.isPending}
        error={treat.error}
        succeeded={treat.isSuccess}
      />
    </div>
  )
}
