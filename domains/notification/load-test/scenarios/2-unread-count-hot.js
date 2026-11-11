// 시나리오 2: 알림이 몰린 회원 1명(9600001, 주문이 많은 판매자)이 쉬지 않고 안 읽은 개수를 묻는다 → 모두 200, count = 180000
// 여러 탭 · 기기에서 같은 회원이 배지를 부르는 최악의 경우
import { countUnread, loadOptions, memberAuth } from '../lib/common.js';

const MEMBER_ID = 9600001;
const UNREAD = 180000;

export const options = loadOptions();

export function setup() {
  return { member: memberAuth(MEMBER_ID) };
}

export default function (data) {
  countUnread(data.member, UNREAD);
}
