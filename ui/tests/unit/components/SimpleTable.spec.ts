import { shallowMount, VueWrapper } from "@vue/test-utils";
import SimpleTable from "@/components/SimpleTable.vue";

function getListOfColumnValues(
  data: {
    firstName: string;
    lastName: string;
    height: string;
    weight: string;
    favouriteAnimal: string;
    fears: string;
    isSuperhero: string;
  }[],
  column: "height" | "weight"
) {
  return data.map((row) => {
    return row[column];
  });
}

describe("SimpleTable", () => {
  const data = [
    {
      firstName: "Bofke",
      lastName: "Dijkstra",
      height: "1.70",
      weight: "60.0",
      favouriteAnimal: "unicorn",
      fears: "vampires",
      isSuperhero: "no",
    },
    {
      firstName: "John",
      lastName: "Doe",
      height: "1.80",
      weight: "80.0",
      favouriteAnimal: "elephant",
      fears: "clowns, spiders",
      isSuperhero: "no",
    },
    {
      firstName: "Jane",
      lastName: "Doe",
      height: "1.74",
      weight: "65.0",
      favouriteAnimal: "unicorn",
      fears: "snakes, heights",
      isSuperhero: "no",
    },
    {
      firstName: "Clark",
      lastName: "Kent",
      height: "1.90",
      weight: "90.0",
      favouriteAnimal: "dog",
      fears: "kryptonite",
      isSuperhero: "yes",
    },
    {
      firstName: "Peter",
      lastName: "Parker",
      height: "1.75",
      weight: "70.0",
      favouriteAnimal: "spider",
      fears: "failure",
      isSuperhero: "yes",
    },
  ];
  let wrapper: VueWrapper<any>;
  beforeEach(function () {
    wrapper = shallowMount(SimpleTable, {
      props: {
        data: data,
        maxWidth: 600,
      },
    });
  });

  test("displays data", () => {
    expect(wrapper.html()).toContain("Bofke");
    expect(wrapper.html()).toContain("Jane");
    expect(wrapper.html()).toContain("clowns, spiders");
    expect(wrapper.html()).toContain("kryptonite");
    expect(wrapper.html()).toContain("failure");
  });

  test("max number of characters", () => {
    // tablekeys length = 7
    // 20 * 7 - (10 / 7 - 1 ) * 50 = 118.57
    // ceil(600 / 118.57) = 6
    expect(wrapper.vm.maxNumberCharacters).toBe(6);
  });

  test("headers are truncated when over 5 + 2 characters", () => {
    expect(wrapper.vm.tableHeader).toEqual([
      "firstN..",
      "lastName",
      "height",
      "weight",
      "favour..",
      "fears",
      "isSupe..",
    ]);
  });

  test("whole floats are converted to ints", () => {
    const actual = getListOfColumnValues(wrapper.vm.dataToPreview, "weight");
    expect(actual).toEqual([60, 80, 65, 90, 70]);
  });

  test("actual floats are not converted to ints", () => {
    const actual = getListOfColumnValues(wrapper.vm.dataToPreview, "height");
    expect(actual).toEqual(["1.70", "1.80", "1.74", "1.90", "1.75"]);
  });
});
