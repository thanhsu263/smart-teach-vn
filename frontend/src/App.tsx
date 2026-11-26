import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { GuestOnly, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './components/AppLayout'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { AttemptPage } from './student/AttemptPage'
import { StartPage } from './student/StartPage'

// Pages people open straight from a link (login, a student's code) are in the main bundle, so they
// render after one download. Pages reached inside the teacher app, and the live quiz with its
// WebSocket client, load on demand.
const pages = {
  classes: () => import('./classes/ClassesPage').then((m) => ({ Component: m.ClassesPage })),
  classDetail: () => import('./classes/ClassDetailPage').then((m) => ({ Component: m.ClassDetailPage })),
  quizzes: () => import('./quizzes/QuizzesPage').then((m) => ({ Component: m.QuizzesPage })),
  quizEditor: () => import('./quizzes/QuizEditorPage').then((m) => ({ Component: m.QuizEditorPage })),
  assignments: () => import('./assignments/AssignmentsPage').then((m) => ({ Component: m.AssignmentsPage })),
  assignmentEditor: () =>
    import('./assignments/AssignmentEditorPage').then((m) => ({ Component: m.AssignmentEditorPage })),
  assignmentDetail: () =>
    import('./assignments/AssignmentDetailPage').then((m) => ({ Component: m.AssignmentDetailPage })),
  admin: () => import('./admin/AdminPage').then((m) => ({ Component: m.AdminPage })),
  host: () => import('./live/HostPage').then((m) => ({ Component: m.HostPage })),
  play: () => import('./live/PlayPage').then((m) => ({ Component: m.PlayPage })),
}

const router = createBrowserRouter([
  {
    // Shown only while the code for a lazy page opened directly is downloading.
    hydrateFallbackElement: <div className="min-h-screen" />,
    children: [
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
              { path: '/classes', lazy: pages.classes },
              { path: '/classes/:id', lazy: pages.classDetail },
              { path: '/quizzes', lazy: pages.quizzes },
              { path: '/quizzes/new', lazy: pages.quizEditor },
              { path: '/quizzes/:id', lazy: pages.quizEditor },
              { path: '/assignments', lazy: pages.assignments },
              { path: '/assignments/new', lazy: pages.assignmentEditor },
              { path: '/assignments/:id', lazy: pages.assignmentDetail },
              { path: '/assignments/:id/edit', lazy: pages.assignmentEditor },
              { path: '/admin', lazy: pages.admin },
            ],
          },
          // Full screen for the projector, without the app menu.
          { path: '/live/:pin', lazy: pages.host },
        ],
      },
      // Opened from the reset mail, so they work whether or not someone is logged in on this browser.
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
      // Students: no account, open even when a teacher is logged in on the same browser.
      { path: '/s', element: <StartPage /> },
      { path: '/s/:code', element: <StartPage /> },
      { path: '/attempts/:id', element: <AttemptPage /> },
      { path: '/play', lazy: pages.play },
      { path: '/play/:pin', lazy: pages.play },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
