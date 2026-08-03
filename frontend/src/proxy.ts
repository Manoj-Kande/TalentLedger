import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

// Routes that require authentication
const protectedRoutes = ['/dashboard', '/contacts', '/companies', '/campaigns', '/saved-lists', '/dumps', '/settings'];
const adminRoutes = ['/admin'];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Check if this is a protected or admin route
  const isProtected = protectedRoutes.some((route) => pathname.startsWith(route));
  const isAdmin = adminRoutes.some((route) => pathname.startsWith(route));

  if (isProtected || isAdmin) {
    // Since auth is localStorage-based, we rely on client-side DashboardShell
    // This proxy just adds the X-Auth-Required header as a hint
    const sessionToken = request.cookies.get('session_token')?.value;

    if (!sessionToken) {
      const response = NextResponse.next();
      response.headers.set('x-auth-required', 'true');
      return response;
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/dashboard/:path*',
    '/contacts/:path*',
    '/companies/:path*',
    '/campaigns/:path*',
    '/saved-lists/:path*',
    '/dumps/:path*',
    '/settings/:path*',
    '/admin/:path*',
  ],
};
