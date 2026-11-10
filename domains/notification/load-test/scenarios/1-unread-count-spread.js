// 시나리오 1: 회원 1만 명(9500001~9510000) 중 무작위 회원이 쉬지 않고 안 읽은 개수를 묻는다 → 모두 200, count = 8
// 헤더 배지처럼 회원마다 안 읽은 알림이 몇 건인 보통의 경우
import { countUnread, loadOptions, memberAuth } from '../lib/common.js';

const FIRST_MEMBER_ID = 9500001;
// token 방식은 회원마다 토큰을 발급하므로 쓰는 회원 수를 줄일 수 있다(기본 1만 명 모두)
const MEMBERS = Number(__ENV.MEMBERS || 10000);
const UNREAD_PER_MEMBER = 8;

export const options = loadOptions();

export function setup() {
  const members = [];
  for (let i = 0; i < MEMBERS; i++) {
    members.push(memberAuth(FIRST_MEMBER_ID + i));
  }
  return { members };
}

export default function (data) {
  const member = data.members[Math.floor(Math.random() * data.members.length)];
  countUnread(member, UNREAD_PER_MEMBER);
}
