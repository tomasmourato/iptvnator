import { SETTINGS_ROUTES } from './settings.routes';

describe('SETTINGS_ROUTES', () => {
    const original = window.matchMedia;

    afterEach(() => {
        window.matchMedia = original;
    });

    function phoneMatch(phone: boolean): boolean {
        window.matchMedia = jest.fn(() => ({
            matches: phone,
        })) as unknown as typeof window.matchMedia;
        const [indexRoute] = SETTINGS_ROUTES;
        const canMatch = indexRoute.canMatch?.[0] as () => boolean;
        return canMatch();
    }

    it('opens the section list on a phone', () => {
        expect(SETTINGS_ROUTES[0].path).toBe('');
        expect(phoneMatch(true)).toBe(true);
    });

    it('opens General on wider screens', () => {
        expect(phoneMatch(false)).toBe(false);
        expect(SETTINGS_ROUTES[1]).toMatchObject({
            path: '',
            redirectTo: 'general',
        });
    });
});
