import http from 'k6/http';
import {check, sleep} from 'k6';

export const options = {
    stages: [
        {duration: '30s', target: 10},
        {duration: '5m', target: 10},
        {duration: '30s', target: 0},
    ],
};

const BASE_URL = 'http://arch.homework';

export default function () {
    const uniqueId = `${__VU}-${__ITER}-${Date.now()}`;

    // CREATE
    const createResponse = http.post(
        `${BASE_URL}/users`,
        JSON.stringify({
            username: `user-${uniqueId}`,
            firstName: 'Load',
            lastName: 'Test',
            email: `user-${uniqueId}@example.com`,
            phone: '+79990000001',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    const created = check(createResponse, {
        'POST /users - 201': (response) => response.status === 201,
    });

    if (!created) {
        sleep(1);
        return;
    }

    const userId = createResponse.json('id');

    // GET
    const getResponse = http.get(`${BASE_URL}/users/${userId}`);

    check(getResponse, {
        'GET /users/{id} - 200': (response) => response.status === 200,
    });

    // UPDATE
    const updateResponse = http.put(
        `${BASE_URL}/users/${userId}`,
        JSON.stringify({
            username: `user-${uniqueId}`,
            firstName: 'Load Updated',
            lastName: 'Test Updated',
            email: `updated-${uniqueId}@example.com`,
            phone: '+79990000002',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(updateResponse, {
        'PUT /users/{id} - 200': (response) => response.status === 200,
    });

    // DELETE
    const deleteResponse = http.del(`${BASE_URL}/users/${userId}`);

    check(deleteResponse, {
        'DELETE /users/{id} - 204': (response) => response.status === 204,
    });

    sleep(1);
}