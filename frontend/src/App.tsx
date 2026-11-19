import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { GuestOnly, RequireAuth } from './auth/RequireAuth'
import { ClassDetailPage } from './classes/ClassDetailPage'
import { ClassesPage } from './classes/ClassesPage'
import { AppLayout } from './components/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <Navigate to="/classes" replace /> },
          { path: '/classes', element: <ClassesPage /> },
          { path: '/classes/:id', element: <ClassDetailPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
