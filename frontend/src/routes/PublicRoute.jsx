import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import FullScreenLoader from '../components/FullScreenLoader.jsx';

export default function PublicRoute() {
  const { loading, isAuthenticated } = useAuth();

  if (loading) return <FullScreenLoader />;
  if (isAuthenticated) return <Navigate to="/home" replace />;
  return <Outlet />;
}
