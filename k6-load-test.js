import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN;
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@test.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'AdminPass123!';

const PROFILE_RATE = Number(__ENV.PROFILE_RATE || 140);
const ADMIN_RATE = Number(__ENV.ADMIN_RATE || 40);
const LOGIN_RATE = Number(__ENV.LOGIN_RATE || 20);
const TEST_DURATION = __ENV.TEST_DURATION || '60s';

if (!ADMIN_TOKEN) {
  throw new Error('ADMIN_TOKEN is required for mixed-load k6 run');
}

const authorizedParams = {
  headers: {
    Authorization: `Bearer ${ADMIN_TOKEN}`,
  },
};

const loginPayload = JSON.stringify({
  email: ADMIN_EMAIL,
  motDePasse: ADMIN_PASSWORD,
});

const loginParams = {
  headers: {
    'Content-Type': 'application/json',
  },
};

export const options = {
  scenarios: {
    profile: {
      executor: 'constant-arrival-rate',
      rate: PROFILE_RATE,
      timeUnit: '1s',
      duration: TEST_DURATION,
      preAllocatedVUs: 80,
      maxVUs: 240,
      exec: 'profile',
    },
    admin: {
      executor: 'constant-arrival-rate',
      rate: ADMIN_RATE,
      timeUnit: '1s',
      duration: TEST_DURATION,
      preAllocatedVUs: 40,
      maxVUs: 120,
      exec: 'admin',
    },
    login: {
      executor: 'constant-arrival-rate',
      rate: LOGIN_RATE,
      timeUnit: '1s',
      duration: TEST_DURATION,
      preAllocatedVUs: 20,
      maxVUs: 80,
      exec: 'login',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<=500'],
  },
};

export function profile() {
  const response = http.get(`${BASE_URL}/profile`, authorizedParams);
  check(response, {
    'profile status is 200': (r) => r.status === 200,
  });
}

export function admin() {
  const response = http.get(`${BASE_URL}/admin/users?page=0&size=20`, authorizedParams);
  check(response, {
    'admin users status is 200': (r) => r.status === 200,
  });
}

export function login() {
  const response = http.post(`${BASE_URL}/login`, loginPayload, loginParams);
  check(response, {
    'login status is 200': (r) => r.status === 200,
    'login returns accessToken': (r) => {
      try {
        const data = JSON.parse(r.body || '{}');
        return Boolean(data.accessToken);
      } catch (error) {
        return false;
      }
    },
  });
}
