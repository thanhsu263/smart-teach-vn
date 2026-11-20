import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { AssignmentDetailPage } from './assignments/AssignmentDetailPage'
import { AssignmentEditorPage } from './assignments/AssignmentEditorPage'
import { AssignmentsPage } from './assignments/AssignmentsPage'
import { GuestOnly, RequireAuth } from './auth/RequireAuth'
import { ClassDetailPage } from './classes/ClassDetailPage'
import { ClassesPage } from './classes/ClassesPage'
import { AppLayout } from './components/AppLayout'
import { HostPage } from './live/HostPage'
import { PlayPage } from './live/PlayPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { QuizEditorPage } from './quizzes/QuizEditorPage'
import { QuizzesPage } from './quizzes/QuizzesPage'
import { AttemptPage } from './student/AttemptPage'
import { StartPage } from './student/StartPage'

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
          { path: '/quizzes', element: <QuizzesPage /> },
          { path: '/quizzes/new', element: <QuizEditorPage /> },
          { path: '/quizzes/:id', element: <QuizEditorPage /> },
          { path: '/assignments', element: <AssignmentsPage /> },
          { path: '/assignments/new', element: <AssignmentEditorPage /> },
          { path: '/assignments/:id', element: <AssignmentDetailPage /> },
          { path: '/assignments/:id/edit', element: <AssignmentEditorPage /> },
        ],
      },
      // Full screen for the projector, without the app menu.
      { path: '/live/:pin', element: <HostPage /> },
    ],
  },
  // Students: no account, open even when a teacher is logged in on the same browser.
  { path: '/s', element: <StartPage /> },
  { path: '/s/:code', element: <StartPage /> },
  { path: '/attempts/:id', element: <AttemptPage /> },
  { path: '/play', element: <PlayPage /> },
  { path: '/play/:pin', element: <PlayPage /> },
  { path: '*', element: <Navigate to="/" replace /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
