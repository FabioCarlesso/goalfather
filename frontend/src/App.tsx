import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { AppToaster } from './components/AppToaster'
import { router } from './router'

export function App() {
  return (
    <AuthProvider>
      <AppToaster />
      <div className="min-h-full bg-slate-950 text-slate-200">
        <RouterProvider router={router} />
      </div>
    </AuthProvider>
  )
}
